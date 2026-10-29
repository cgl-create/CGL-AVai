package com.cgl.avai

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider

@Composable
fun CglAccountsDialog(
    onDismiss: () -> Unit,
    onStatus: (String) -> Unit,
    onAccountChanged: (String) -> Unit
) {
    val context = LocalContext.current
    val auth = remember { try {
        if (FirebaseApp.getApps(context).isEmpty()) FirebaseApp.initializeApp(context)
        if (FirebaseApp.getApps(context).isEmpty()) null else FirebaseAuth.getInstance()
    } catch (_: Exception) { null } }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var createAccount by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by remember {
        mutableStateOf(if (auth == null) "Firebase configuration is missing for this app. Add this package to the existing cgl-accounts Firebase project and place its google-services.json in this app module." else auth.currentUser?.email?.let { "Signed in as $it" } ?: "Sign in with your CGL+ Account.")
    }
    val googleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK || result.data == null) {
            message = "Google sign-in was cancelled or did not complete."
        } else if (auth == null) {
            message = "Firebase is not configured for this app."
        } else {
            try {
                val account = GoogleSignIn.getSignedInAccountFromIntent(result.data)
                    .getResult(ApiException::class.java)
                val credential = GoogleAuthProvider.getCredential(account.idToken, null)
                busy = true
                auth.signInWithCredential(credential).addOnCompleteListener { task ->
                    busy = false
                    if (task.isSuccessful) {
                        val label = auth.currentUser?.email ?: "CGL+ Account"
                        message = "Signed in as $label"
                        onAccountChanged(label)
                        onStatus("CGL+ Account connected")
                    } else {
                        message = task.exception?.localizedMessage ?: "Google sign-in failed."
                    }
                }
            } catch (error: Exception) {
                message = error.localizedMessage ?: "Google sign-in failed. Check Firebase's OAuth configuration and SHA fingerprints."
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("CGL+ Accounts") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Use the same CGL+ Account identity across AVai and Friend. Chats and API keys are not uploaded by signing in.", fontSize = 12.sp)
                auth?.currentUser?.email?.let { Text("Current account: $it", fontSize = 12.sp) }
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it.trim() },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Email") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    singleLine = true
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Password") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    singleLine = true
                )
                Text(message, fontSize = 11.sp)
                if (auth == null) {
                    Text("Sign-in is disabled until the real Firebase app configuration is installed.", fontSize = 11.sp)
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TextButton(
                    enabled = auth != null && !busy,
                    onClick = {
                        val current = auth ?: return@TextButton
                        if (current.currentUser != null) {
                            current.signOut()
                            message = "Signed out"
                            onAccountChanged("Sign in")
                            onStatus("Signed out of CGL+ Accounts")
                        } else if (email.isBlank() || password.length < 6) {
                            message = "Enter an email and a password with at least 6 characters."
                        } else {
                            busy = true
                            val task = if (createAccount) current.createUserWithEmailAndPassword(email, password)
                                else current.signInWithEmailAndPassword(email, password)
                            task.addOnCompleteListener { result ->
                                busy = false
                                if (result.isSuccessful) {
                                    val label = current.currentUser?.email ?: "CGL+ Account"
                                    message = "Signed in as $label"
                                    onAccountChanged(label)
                                    onStatus(if (createAccount) "CGL+ Account created" else "CGL+ Account connected")
                                } else {
                                    message = result.exception?.localizedMessage ?: "Authentication failed. Check the account and enabled provider."
                                }
                            }
                        }
                    }
                ) { Text(if (busy) "Please wait…" else if (auth?.currentUser != null) "Sign out" else if (createAccount) "Create" else "Sign in") }
                TextButton(onClick = { createAccount = !createAccount; message = if (createAccount) "Create a CGL+ Account with email and password." else "Sign in with your existing CGL+ Account." }) {
                    Text(if (createAccount) "Use sign-in" else "Create account")
                }
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(
                    enabled = auth != null && !busy,
                    onClick = {
                        val current = auth ?: return@TextButton
                        val resId = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
                        if (resId == 0) {
                            message = "Google sign-in needs the app's real google-services.json and OAuth web client ID."
                        } else {
                            try {
                                val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                                    .requestEmail()
                                    .requestIdToken(context.getString(resId))
                                    .build()
                                message = "Opening Google sign-in…"
                                googleLauncher.launch(GoogleSignIn.getClient(context, options).signInIntent)
                            } catch (error: Exception) {
                                message = error.localizedMessage ?: "Could not start Google sign-in."
                            }
                        }
                    }
                ) { Text("Google") }
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        }
    )
}
