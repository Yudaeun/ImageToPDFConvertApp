package com.itp.imagetopdfapp

import android.app.DownloadManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import androidx.core.os.postDelayed
import coil.compose.AsyncImage
import com.itp.imagetopdfapp.ui.theme.ImageToPDFAppTheme
import com.itp.imagetopdfapp.viewModel.PdfViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.logging.Handler
import kotlin.jvm.java

class MainActivity : ComponentActivity() {
    private val viewModel: PdfViewModel by viewModels()
    private val CHANNEL_ID = "pdf_download_channel"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        createNotificationChannel(this)

        setContent {
            PdfConverterApp(viewModel)
        }
    }


    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            val channel = NotificationChannel(
                CHANNEL_ID,
                "PDF 채널",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "PDF 저장 완료 알림 채널"
            }

            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            notificationManager.createNotificationChannel(channel)
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun PdfConverterApp(viewModel: PdfViewModel) {
        val ctx = LocalContext.current
        val selectedImages = viewModel.selectedImages

        val pickImagesLauncher =
            rememberLauncherForActivityResult(
                contract = ActivityResultContracts.PickMultipleVisualMedia(50)
            ) { uris ->
                if (uris.isNotEmpty()) {
                    viewModel.updateImages(uris)
                }
            }

        Scaffold(
            topBar = {
                TopAppBar(title = { Text("PDF 변환")})
            }
        ) { padding ->

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(20.dp)
            ) {
                Button(
                    onClick = {
                        pickImagesLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("이미지 선택하기 (최대 50장)")
                }

                Spacer(Modifier.height(16.dp))

                LazyVerticalGrid(
                    columns = GridCells.Adaptive(100.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(selectedImages.size) { idx ->
                        AsyncImage(
                            model = selectedImages[idx],
                            contentDescription = null,
                            modifier = Modifier
                                .padding(4.dp)
                                .size(100.dp)
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                Button(
                    onClick = {
                        viewModel.convertPdf(
                            context = ctx,
                            onSuccess = { file ->
                                val notificationId = 1001

                                val notificationManager =
                                    ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

                                val pdfUri = FileProvider.getUriForFile(
                                    ctx,
                                    "${ctx.packageName}.provider",
                                    file
                                )

                                val openIntent = Intent(Intent.ACTION_VIEW).apply {
                                    setDataAndType(pdfUri, "application/pdf")
                                    flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
                                }

                                val pendingIntent = PendingIntent.getActivity(
                                    ctx,
                                    0,
                                    openIntent,
                                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                                )

                                val builder = NotificationCompat.Builder(ctx, CHANNEL_ID)
                                    .setSmallIcon(android.R.drawable.ic_menu_save)
                                    .setContentTitle("PDF 저장 중…")
                                    .setContentText("PDF를 저장하고 있어요!")
                                    .setOngoing(true)
                                    .setProgress(0, 0, true)

                                notificationManager.notify(notificationId, builder.build())

                                CoroutineScope(Dispatchers.Main).launch {
//                                    delay(700L)

                                    builder.setContentTitle("PDF 저장 완료")
                                        .setContentText("${file.name} 이 저장되었어요!")
                                        .setOngoing(false)
                                        .setProgress(0, 0, false)
                                        .setAutoCancel(true)
                                        .setContentIntent(pendingIntent)

                                    notificationManager.notify(notificationId, builder.build())

                                    Toast.makeText(ctx, "PDF 저장 완료!", Toast.LENGTH_SHORT).show()
                                }
                            }
                            ,
                            onError = { Toast.makeText(ctx, it, Toast.LENGTH_SHORT).show() }
                        )

                    },
                    enabled = selectedImages.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("PDF 생성하기")
                }

                Spacer(Modifier.height(10.dp))

                Button(
                    onClick = {
                        viewModel.openPdf(ctx)
                    },
                    enabled = viewModel.lastPdf != null,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("PDF 열기")
                }

                Spacer(Modifier.height(10.dp))

                Button(
                    onClick = {
                        viewModel.sharePdf(ctx)
                    },
                    enabled = viewModel.lastPdf != null,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("PDF 공유하기")
                }
            }
        }
    }

}
