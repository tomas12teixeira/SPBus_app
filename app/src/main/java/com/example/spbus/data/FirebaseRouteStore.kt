package com.example.spbus.data

import android.content.Context
import com.example.spbus.BuildConfig
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

data class SavedTransitRoute(
    val origin: String,
    val destination: String,
    val line: String,
    val updatedAt: String
)

class FirebaseRouteStore(context: Context) {
    private val app = createFirebaseApp(context.applicationContext)
    private val auth = app?.let(FirebaseAuth::getInstance)
    private val firestore = app?.let(FirebaseFirestore::getInstance)

    val isConfigured: Boolean get() = app != null

    fun saveRoute(
        origin: String,
        destination: String,
        line: String,
        favorite: Boolean,
        callback: (String?) -> Unit
    ) {
        withUser(callback) { user ->
            val collection = if (favorite) "favorites" else "history"
            val documentId = if (favorite) {
                listOf(origin, destination, line).joinToString("_") { it.hashCode().toUInt().toString(16) }
            } else {
                null
            }
            val routes = firestore?.collection("users")?.document(user.uid)?.collection(collection)
            val document = documentId?.let { routes?.document(it) } ?: routes?.document()
            if (document == null) {
                callback("Firebase nao esta disponivel neste momento.")
                return@withUser
            }
            document.set(
                mapOf(
                    "origin" to origin,
                    "destination" to destination,
                    "line" to line,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).addOnSuccessListener { callback(null) }
                .addOnFailureListener { callback(it.localizedMessage ?: "Falha ao salvar no Firebase.") }
        }
    }

    fun loadRoutes(favorites: Boolean, callback: (List<SavedTransitRoute>, String?) -> Unit) {
        withUser({ error -> callback(emptyList(), error) }) { user ->
            val collection = if (favorites) "favorites" else "history"
            firestore?.collection("users")?.document(user.uid)?.collection(collection)
                ?.orderBy("updatedAt", Query.Direction.DESCENDING)
                ?.limit(12)
                ?.get()
                ?.addOnSuccessListener { snapshot ->
                    val routes = snapshot.documents.mapNotNull { document ->
                        val origin = document.getString("origin") ?: return@mapNotNull null
                        val destination = document.getString("destination") ?: return@mapNotNull null
                        SavedTransitRoute(
                            origin = origin,
                            destination = destination,
                            line = document.getString("line").orEmpty(),
                            updatedAt = document.getTimestamp("updatedAt")?.toDate()?.toString().orEmpty()
                        )
                    }
                    callback(routes, null)
                }
                ?.addOnFailureListener { callback(emptyList(), it.localizedMessage ?: "Falha ao ler dados do Firebase.") }
                ?: callback(emptyList(), "Firebase nao esta configurado.")
        }
    }

    private fun withUser(onError: (String?) -> Unit, action: (com.google.firebase.auth.FirebaseUser) -> Unit) {
        val firebaseAuth = auth
        if (firebaseAuth == null) {
            onError("Configure firebase.api_key, firebase.app_id e firebase.project_id em local.properties.")
            return
        }
        firebaseAuth.currentUser?.let {
            action(it)
            return
        }
        firebaseAuth.signInAnonymously()
            .addOnSuccessListener { result -> result.user?.let(action) ?: onError("Firebase nao retornou um usuario.") }
            .addOnFailureListener { onError(it.localizedMessage ?: "Ative a autenticacao anonima no Firebase.") }
    }

    private fun createFirebaseApp(context: Context): FirebaseApp? {
        val apiKey = BuildConfig.FIREBASE_API_KEY.trim()
        val appId = BuildConfig.FIREBASE_APP_ID.trim()
        val projectId = BuildConfig.FIREBASE_PROJECT_ID.trim()
        if (apiKey.isBlank() || appId.isBlank() || projectId.isBlank()) return null
        return try {
            FirebaseApp.getApps(context).firstOrNull { it.name == FIREBASE_APP_NAME }
                ?: FirebaseApp.initializeApp(
                    context,
                    FirebaseOptions.Builder()
                        .setApiKey(apiKey)
                        .setApplicationId(appId)
                        .setProjectId(projectId)
                        .build(),
                    FIREBASE_APP_NAME
                )
        } catch (_: IllegalStateException) {
            null
        }
    }

    companion object {
        private const val FIREBASE_APP_NAME = "spbus"
    }
}