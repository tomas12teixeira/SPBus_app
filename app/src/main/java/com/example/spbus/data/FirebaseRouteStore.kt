package com.example.spbus.data

import android.content.Context
import com.google.firebase.FirebaseApp
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
    val isSignedIn: Boolean get() = auth?.currentUser != null
    val accountEmail: String? get() = auth?.currentUser?.email

    fun register(email: String, password: String, callback: (String?) -> Unit) {
        val firebaseAuth = auth
        if (firebaseAuth == null) {
            callback(firebaseSetupMessage())
            return
        }
        firebaseAuth.createUserWithEmailAndPassword(email.trim(), password)
            .addOnSuccessListener { result ->
                val user = result.user
                val profile = user?.let {
                    firestore?.collection("users")?.document(it.uid)?.set(
                        mapOf(
                            "uid" to it.uid,
                            "email" to it.email,
                            "createdAt" to FieldValue.serverTimestamp(),
                            "preferences" to mapOf("theme" to "system")
                        )
                    )
                }
                if (profile == null) callback("Firebase Firestore nao esta configurado.")
                else profile.addOnSuccessListener { callback(null) }
                    .addOnFailureListener { callback(it.localizedMessage ?: "Conta criada, mas o perfil nao foi salvo.") }
            }
            .addOnFailureListener { callback(it.localizedMessage ?: "Nao foi possivel criar a conta.") }
    }

    fun signIn(email: String, password: String, callback: (String?) -> Unit) {
        val firebaseAuth = auth
        if (firebaseAuth == null) {
            callback(firebaseSetupMessage())
            return
        }
        firebaseAuth.signInWithEmailAndPassword(email.trim(), password)
            .addOnSuccessListener { callback(null) }
            .addOnFailureListener { callback(it.localizedMessage ?: "Nao foi possivel entrar.") }
    }

    fun signOut() {
        auth?.signOut()
    }

    fun saveRoute(
        origin: String,
        destination: String,
        line: String,
        favorite: Boolean,
        callback: (String?) -> Unit
    ) {
        withUser(callback) { user ->
            val collection = if (favorite) "favorite_routes" else "history"
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
            val collection = if (favorites) "favorite_routes" else "history"
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

    fun saveFavoriteLine(lineCode: String, callback: (String?) -> Unit) {
        withUser(callback) { user ->
            val document = firestore?.collection("users")?.document(user.uid)
                ?.collection("favorite_lines")?.document(lineCode.hashCode().toUInt().toString(16))
            if (document == null) {
                callback("Firebase nao esta configurado.")
                return@withUser
            }
            document.set(mapOf("lineCode" to lineCode, "updatedAt" to FieldValue.serverTimestamp()))
                .addOnSuccessListener { callback(null) }
                .addOnFailureListener { callback(it.localizedMessage ?: "Falha ao salvar a linha favorita.") }
        }
    }

    fun saveFeedback(lineCode: String, category: String, comment: String, callback: (String?) -> Unit) {
        withUser(callback) { user ->
            val collection = firestore?.collection("feedbacks")
            if (collection == null) {
                callback("Firebase nao esta configurado.")
                return@withUser
            }
            collection.add(
                mapOf(
                    "uid" to user.uid,
                    "lineCode" to lineCode.trim(),
                    "category" to category,
                    "comment" to comment.trim(),
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).addOnSuccessListener { callback(null) }
                .addOnFailureListener { callback(it.localizedMessage ?: "Falha ao salvar o relato.") }
        }
    }

    private fun withUser(onError: (String?) -> Unit, action: (com.google.firebase.auth.FirebaseUser) -> Unit) {
        val firebaseAuth = auth
        if (firebaseAuth == null) {
            onError(firebaseSetupMessage())
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

    private fun createFirebaseApp(context: Context): FirebaseApp? =
        FirebaseApp.getApps(context).firstOrNull { it.name == FirebaseApp.DEFAULT_APP_NAME }
            ?: runCatching { FirebaseApp.initializeApp(context) }.getOrNull()

    private fun firebaseSetupMessage() =
        "Adicione o google-services.json do seu projeto em app/ e habilite Email/Senha e autenticacao anonima no Firebase."

}