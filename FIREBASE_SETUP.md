# Firebase Setup

1. Register the Android app with application ID `com.example.spbus` in Firebase Console.
2. Download `google-services.json` and place it in `app/google-services.json`. Keep it out of public repositories when project policy requires it.
3. Enable Email/Password and Anonymous providers in Firebase Authentication.
4. Create a Cloud Firestore database and publish rules similar to the following, then tighten them to match production moderation requirements.

```text
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    function signedInAs(uid) {
      return request.auth != null && request.auth.uid == uid;
    }

    match /users/{uid} {
      allow read, write: if signedInAs(uid);
    }

    match /users/{uid}/{collection}/{document} {
      allow read, write: if signedInAs(uid);
    }

    match /feedbacks/{feedbackId} {
      allow create: if request.auth != null
        && request.resource.data.uid == request.auth.uid
        && request.resource.data.category is string
        && request.resource.data.comment is string
        && request.resource.data.comment.size() <= 500;
      allow read, update, delete: if false;
    }
  }
}
```

The app does not create or commit Firebase credentials. Without `app/google-services.json`, Authentication and Firestore remain unavailable while public route lookup still works. Anonymous users can save their own data; signing in with Email/Password is needed to retain it across devices.
