
package com.ats.sentinel

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight

@Composable
fun NativeRuntimeTestCard() {

    var testStatus by remember {
        mutableStateOf("NOT TESTED")
    }

    var statusColor by remember {
        mutableStateOf(Color(0xFF7895B5))
    }

    var isTesting by remember {
        mutableStateOf(false)
    }

    val libraryLoaded = NativeRuntimeBridge.isLibraryLoaded

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF101D30)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(17.dp)
        ) {

            Text(
                text = "NATIVE RUNTIME TEST",
                color = Color(0xFF00BFFF),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "JNI LIBRARY",
                color = Color(0xFF7895B5),
                fontSize = 10.sp
            )

            Text(
                text = if (libraryLoaded)
                    "LIBRARY LOADED"
                else
                    "LIBRARY NOT LOADED",
                color = if (libraryLoaded)
                    Color(0xFF35D69A)
                else
                    Color(0xFFFF6262),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(13.dp))

            Text(
                text = "NATIVE FUNCTION",
                color = Color(0xFF7895B5),
                fontSize = 10.sp
            )

            Text(
                text = testStatus,
                color = statusColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(15.dp))

            Button(
                onClick = {
                    isTesting = true

                    try {
                        if (!libraryLoaded) {
                            testStatus = "LIBRARY LOAD FAILED"
                            statusColor = Color(0xFFFF6262)
                        } else {
                            val result =
                                NativeRuntimeBridge.nativeStatus()

                            testStatus = result
                            statusColor = Color(0xFF35D69A)
                        }
                    } catch (error: Throwable) {
                        testStatus =
                            "JNI CALL FAILED: ${error.javaClass.simpleName}"

                        statusColor = Color(0xFFFF6262)
                    }

                    isTesting = false
                },
                enabled = libraryLoaded && !isTesting,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp),
                shape = RoundedCornerShape(11.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF07568A)
                )
            ) {
                Text(
                    text = if (isTesting)
                        "TESTING..."
                    else
                        "RUN JNI TEST"
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "LOCAL TEST • OFFLINE • LLAMA CPP PENDING",
                color = Color(0xFF7895B5),
                fontSize = 9.sp,
                letterSpacing = 1.sp
            )
        }
    }
}
