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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.size

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff

import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

import com.jos.myapplication.components.AuthLogo
import com.jos.myapplication.ui.theme.BackgroundLight
import com.jos.myapplication.ui.theme.BorderGray
import com.jos.myapplication.ui.theme.ChatSphereTheme
import com.jos.myapplication.ui.theme.PurplePrimary
import com.jos.myapplication.ui.theme.SurfaceWhite
import com.jos.myapplication.ui.theme.TextPrimary
import com.jos.myapplication.ui.theme.TextSecondary


@Composable
fun CreateAccountScreen(
    onCreateAccountClick: () -> Unit,
    onNavigateToLogin: () -> Unit
) {

    // -----------------------------
    // Form fields
    // -----------------------------

    var fullName by remember {
        mutableStateOf("")
    }

    var email by remember {
        mutableStateOf("")
    }

    var password by remember {
        mutableStateOf("")
    }

    var confirmPassword by remember {
        mutableStateOf("")
    }

    // -----------------------------
    // Password visibility
    // -----------------------------

    var passwordVisible by remember {
        mutableStateOf(false)
    }

    var confirmPasswordVisible by remember {
        mutableStateOf(false)
    }

    // -----------------------------
    // Terms
    // -----------------------------

    var termsAccepted by remember {
        mutableStateOf(false)
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
                        modifier = Modifier.height(20.dp)
                    )

                    // Title
                    Text(
                        text = "Create Account",
                        style = MaterialTheme.typography.headlineMedium,
                        color = TextPrimary
                    )

                    Spacer(
                        modifier = Modifier.height(6.dp)
                    )

                    // Subtitle
                    Text(
                        text = "Join Just Chat to start connecting.",
                        fontSize = 14.sp,
                        color = TextSecondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(
                        modifier = Modifier.height(24.dp)
                    )

                    // -----------------------------
                    // Full name
                    // -----------------------------

                    OutlinedTextField(
                        value = fullName,

                        onValueChange = {
                            fullName = it
                        },

                        placeholder = {
                            Text("Full Name")
                        },

                        singleLine = true,

                        shape = RoundedCornerShape(14.dp),

                        colors = fieldColors,

                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(
                        modifier = Modifier.height(14.dp)
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
                        modifier = Modifier.height(14.dp)
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
                            if (passwordVisible) {
                                VisualTransformation.None
                            } else {
                                PasswordVisualTransformation()
                            },

                        trailingIcon = {

                            IconButton(
                                onClick = {
                                    passwordVisible =
                                        !passwordVisible
                                }
                            ) {

                                Icon(
                                    imageVector =
                                        if (passwordVisible) {
                                            Icons.Default.VisibilityOff
                                        } else {
                                            Icons.Default.Visibility
                                        },

                                    contentDescription =
                                        if (passwordVisible) {
                                            "Hide password"
                                        } else {
                                            "Show password"
                                        },

                                    tint = TextSecondary
                                )
                            }
                        },

                        shape = RoundedCornerShape(14.dp),

                        colors = fieldColors,

                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(
                        modifier = Modifier.height(14.dp)
                    )

                    // -----------------------------
                    // Confirm password
                    // -----------------------------

                    OutlinedTextField(
                        value = confirmPassword,

                        onValueChange = {
                            confirmPassword = it
                        },

                        placeholder = {
                            Text("Confirm Password")
                        },

                        singleLine = true,

                        visualTransformation =
                            if (confirmPasswordVisible) {
                                VisualTransformation.None
                            } else {
                                PasswordVisualTransformation()
                            },

                        trailingIcon = {

                            IconButton(
                                onClick = {
                                    confirmPasswordVisible =
                                        !confirmPasswordVisible
                                }
                            ) {

                                Icon(
                                    imageVector =
                                        if (confirmPasswordVisible) {
                                            Icons.Default.VisibilityOff
                                        } else {
                                            Icons.Default.Visibility
                                        },

                                    contentDescription =
                                        if (confirmPasswordVisible) {
                                            "Hide password"
                                        } else {
                                            "Show password"
                                        },

                                    tint = TextSecondary
                                )
                            }
                        },

                        shape = RoundedCornerShape(14.dp),

                        colors = fieldColors,

                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(
                        modifier = Modifier.height(16.dp)
                    )

                    // -----------------------------
                    // Terms text
                    // -----------------------------

                    val termsText = buildAnnotatedString {

                        append("I agree to ")

                        withStyle(
                            SpanStyle(
                                color = PurplePrimary,
                                fontWeight = FontWeight.SemiBold
                            )
                        ) {
                            append("Terms of Service")
                        }

                        append(" and ")

                        withStyle(
                            SpanStyle(
                                color = PurplePrimary,
                                fontWeight = FontWeight.SemiBold
                            )
                        ) {
                            append("Privacy Policy")
                        }
                    }

                    // -----------------------------
                    // Terms checkbox
                    // -----------------------------

                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {

                        Checkbox(
                            checked = termsAccepted,

                            onCheckedChange = {
                                termsAccepted = it
                            },

                            colors = CheckboxDefaults.colors(
                                checkedColor = PurplePrimary
                            )
                        )

                        Text(
                            text = termsText,
                            fontSize = 13.sp,
                            color = TextSecondary
                        )
                    }

                    Spacer(
                        modifier = Modifier.height(16.dp)
                    )

                    // -----------------------------
                    // Create Account button
                    // -----------------------------

                    Button(

                        onClick = {

                            // Check empty fields
                            if (
                                fullName.isBlank() ||
                                email.isBlank() ||
                                password.isBlank() ||
                                confirmPassword.isBlank()
                            ) {

                                Toast.makeText(
                                    context,
                                    "Please fill all fields",
                                    Toast.LENGTH_SHORT
                                ).show()

                                return@Button
                            }

                            // Check password
                            if (password != confirmPassword) {

                                Toast.makeText(
                                    context,
                                    "Passwords do not match",
                                    Toast.LENGTH_SHORT
                                ).show()

                                return@Button
                            }

                            // Check terms
                            if (!termsAccepted) {

                                Toast.makeText(
                                    context,
                                    "Please accept the terms",
                                    Toast.LENGTH_SHORT
                                ).show()

                                return@Button
                            }

                            // ---------------------------------
                            // Firebase is created ONLY after
                            // the user presses the button.
                            // This keeps Compose Preview working.
                            // ---------------------------------

                            val auth = FirebaseAuth.getInstance()
                            val db = FirebaseFirestore.getInstance()

                            // ---------------------------------
                            // Create Firebase account
                            // ---------------------------------

                            auth.createUserWithEmailAndPassword(
                                email.trim(),
                                password
                            )

                                .addOnCompleteListener { task ->

                                    if (task.isSuccessful) {

                                        val user = auth.currentUser

                                        if (user != null) {

                                            // -----------------------------
                                            // Save user information
                                            // -----------------------------

                                            val userData =
                                                hashMapOf(
                                                    "name" to fullName.trim(),
                                                    "email" to email.trim()
                                                )

                                            db.collection("users")
                                                .document(user.uid)
                                                .set(userData)

                                                .addOnSuccessListener {

                                                    Toast.makeText(
                                                        context,
                                                        "Account created successfully",
                                                        Toast.LENGTH_SHORT
                                                    ).show()

                                                    // Go to Login
                                                    onCreateAccountClick()
                                                }

                                                .addOnFailureListener {

                                                    Toast.makeText(
                                                        context,
                                                        "Account created, but profile save failed",
                                                        Toast.LENGTH_LONG
                                                    ).show()

                                                    // Still go to Login
                                                    onCreateAccountClick()
                                                }
                                        }

                                    } else {

                                        Toast.makeText(
                                            context,
                                            task.exception?.message
                                                ?: "Registration failed",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
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
                            text = "Create Account",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )

                        Spacer(
                            modifier = Modifier.width(8.dp)
                        )

                        Icon(
                            imageVector = Icons.Default.ArrowForward,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(
                modifier = Modifier.height(24.dp)
            )

            // -----------------------------
            // Login link
            // -----------------------------

            Row {

                Text(
                    text = "Already have an account? ",
                    color = TextSecondary,
                    fontSize = 14.sp
                )

                Text(
                    text = "Login",

                    color = PurplePrimary,

                    fontWeight = FontWeight.Bold,

                    fontSize = 14.sp,

                    modifier = Modifier.clickable {
                        onNavigateToLogin()
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
fun CreateAccountScreenPreview() {

    ChatSphereTheme {

        CreateAccountScreen(
            onCreateAccountClick = {},
            onNavigateToLogin = {}
        )
    }
}