package com.jos.myapplication.screens.auth

import android.widget.Toast

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll

import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text

import androidx.compose.runtime.rememberCoroutineScope
import com.jos.myapplication.ChatSphereApp
import com.jos.myapplication.network.awaitResult
import kotlinx.coroutines.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import com.google.firebase.auth.FirebaseAuth

import com.jos.myapplication.components.AuthLogo
import com.jos.myapplication.ui.theme.BackgroundLight
import com.jos.myapplication.ui.theme.BorderGray
import com.jos.myapplication.ui.theme.ChatSphereTheme
import com.jos.myapplication.ui.theme.PurplePrimary
import com.jos.myapplication.ui.theme.SurfaceWhite
import com.jos.myapplication.ui.theme.TextPrimary
import com.jos.myapplication.ui.theme.TextSecondary


@Composable
fun LoginScreen(
    onLoginClick: () -> Unit,
    onNavigateToCreateAccount: () -> Unit
) {

    // -----------------------------
    // Form fields
    // -----------------------------

    val scope = rememberCoroutineScope()
    var submitting by remember { mutableStateOf(false) }

    var email by remember {
        mutableStateOf("")
    }

    var password by remember {
        mutableStateOf("")
    }

    // -----------------------------
    // Android context
    // -----------------------------

    val context = LocalContext.current

    // -----------------------------
    // Text field colors
    // -----------------------------

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = PurplePrimary,
        unfocusedBorderColor = BorderGray,
        cursorColor = PurplePrimary,
        focusedContainerColor = SurfaceWhite,
        unfocusedContainerColor = SurfaceWhite
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundLight)
    ) {

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(
                    horizontal = 24.dp,
                    vertical = 32.dp
                ),

            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            // -----------------------------
            // Main card
            // -----------------------------

            Card(
                shape = RoundedCornerShape(28.dp),

                colors = CardDefaults.cardColors(
                    containerColor = SurfaceWhite
                ),

                elevation = CardDefaults.cardElevation(
                    defaultElevation = 2.dp
                ),

                modifier = Modifier.fillMaxWidth()
            ) {

                Column(
                    modifier = Modifier.padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {

                    // Logo
                    AuthLogo()

                    Spacer(
                        modifier = Modifier.height(24.dp)
                    )

                    // Title
                    Text(
                        text = "Welcome Back",
                        style = MaterialTheme.typography.headlineMedium,
                        color = TextPrimary
                    )

                    Spacer(
                        modifier = Modifier.height(28.dp)
                    )

                    // -----------------------------
                    // Email
                    // -----------------------------

                    OutlinedTextField(
                        value = email,

                        onValueChange = {
                            email = it
                        },

                        placeholder = {
                            Text("Email")
                        },

                        singleLine = true,

                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email
                        ),

                        shape = RoundedCornerShape(14.dp),

                        colors = fieldColors,

                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(
                        modifier = Modifier.height(16.dp)
                    )

                    // -----------------------------
                    // Password
                    // -----------------------------

                    OutlinedTextField(
                        value = password,

                        onValueChange = {
                            password = it
                        },

                        placeholder = {
                            Text("Password")
                        },

                        singleLine = true,

                        visualTransformation =
                            PasswordVisualTransformation(),

                        shape = RoundedCornerShape(14.dp),

                        colors = fieldColors,

                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(
                        modifier = Modifier.height(12.dp)
                    )

                    // -----------------------------
                    // Forgot password
                    // -----------------------------

                    Text(
                        text = "Forgot Password?",

                        color = PurplePrimary,

                        fontSize = 14.sp,

                        fontWeight = FontWeight.Medium,

                        modifier = Modifier
                            .align(Alignment.End)
                            .clickable {

                                Toast.makeText(
                                    context,
                                    "Password reset is not implemented yet",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                    )

                    Spacer(
                        modifier = Modifier.height(20.dp)
                    )

                    // -----------------------------
                    // Login button
                    // -----------------------------

                    Button(
                        enabled = !submitting,

                        onClick = {

                            // Check empty fields
                            if (
                                email.isBlank() ||
                                password.isBlank()
                            ) {

                                Toast.makeText(
                                    context,
                                    "Please enter email and password",
                                    Toast.LENGTH_SHORT
                                ).show()

                                return@Button
                            }

                            // -----------------------------
                            // Create Firebase Auth only
                            // when the button is clicked
                            // -----------------------------

                            if (submitting) return@Button
                            submitting = true
                            scope.launch {
                                try {
                                    val auth = FirebaseAuth.getInstance()
                                    withTimeout(25_000) { auth.signInWithEmailAndPassword(email.trim(), password).awaitResult() }
                                    val user = auth.currentUser ?: error("Sign in failed")
                                    // Repair a missing profile from an interrupted registration without overwriting an existing name.
                                    runCatching {
                                        withTimeout(15_000) {
                                            val ref = com.google.firebase.firestore.FirebaseFirestore.getInstance().collection("users").document(user.uid)
                                            val profile = ref.get().awaitResult()
                                            if (!profile.exists()) ref.set(mapOf("name" to (user.displayName?.takeIf { it.isNotBlank() } ?: user.email.orEmpty().substringBefore('@')).take(80), "email" to user.email.orEmpty())).awaitResult()
                                        }
                                    }
                                    onLoginClick()
                                } catch (e: Exception) {
                                    if (e is CancellationException && e !is TimeoutCancellationException) throw e
                                    Toast.makeText(context, "Could not sign in. Check connection, email and password, then retry.", Toast.LENGTH_LONG).show()
                                } finally { submitting = false }
                            }
                        },

                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),

                        shape = RoundedCornerShape(50),

                        colors = ButtonDefaults.buttonColors(
                            containerColor = PurplePrimary
                        )
                    ) {

                        Text(
                            text = "Login",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                }
            }

            Spacer(
                modifier = Modifier.height(24.dp)
            )

            // -----------------------------
            // Create account link
            // -----------------------------

            Row {

                Text(
                    text = "Don't have an account? ",
                    color = TextSecondary,
                    fontSize = 14.sp
                )

                Text(
                    text = "Create Account",

                    color = PurplePrimary,

                    fontWeight = FontWeight.Bold,

                    fontSize = 14.sp,

                    modifier = Modifier.clickable {
                        onNavigateToCreateAccount()
                    }
                )
            }
        }
    }
}


// =====================================================
// PREVIEW
// =====================================================

@Preview(showBackground = true)
@Composable
fun LoginScreenPreview() {

    ChatSphereTheme {

        LoginScreen(
            onLoginClick = {},
            onNavigateToCreateAccount = {}
        )
    }
}
