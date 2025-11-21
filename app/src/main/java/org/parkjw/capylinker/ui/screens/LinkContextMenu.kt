package org.parkjw.capylinker.ui.screens

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.drawable.BitmapDrawable
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.core.content.ContextCompat
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import java.io.File
import java.io.FileOutputStream

@Composable
fun LinkContextMenu(
    link: Link,
    strings: org.parkjw.capylinker.ui.strings.AppStrings,
    onDismiss: () -> Unit,
    onOpen: () -> Unit,
    onCopyUrl: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    onReSummarize: () -> Unit
) {
    val context = LocalContext.current
    var showQRDialog by remember { mutableStateOf(false) }

    if (showQRDialog) {
        QRCodeDialog(
            url = link.url,
            strings = strings,
            onDismiss = { showQRDialog = false }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.linkOptions) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                data = android.net.Uri.parse(link.url)
                            }
                            context.startActivity(intent)
                        } catch (e: ActivityNotFoundException) {
                            Toast.makeText(
                                context,
                                "링크를 열 수 없습니다. URL을 확인해주세요.",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        onOpen()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        painter = painterResource(android.R.drawable.ic_menu_view),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(strings.open)
                }

                OutlinedButton(
                    onClick = {
                        val clipboard =
                            context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("URL", link.url)
                        clipboard.setPrimaryClip(clip)
                        onCopyUrl()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        painter = painterResource(android.R.drawable.ic_menu_edit),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(strings.copyUrl)
                }

                OutlinedButton(
                    onClick = {
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, "${link.title}\n\n${link.url}")
                        }
                        context.startActivity(Intent.createChooser(intent, "Share Link"))
                        onShare()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(strings.share)
                }

                OutlinedButton(
                    onClick = {
                        showQRDialog = true
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        painter = painterResource(android.R.drawable.ic_menu_gallery),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(strings.shareQrCode)
                }

                OutlinedButton(
                    onClick = onReSummarize,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(strings.reSummarize)
                }

                OutlinedButton(
                    onClick = onDelete,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(strings.delete)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.cancel)
            }
        }
    )
}

@Composable
fun QRCodeDialog(
    url: String,
    strings: org.parkjw.capylinker.ui.strings.AppStrings,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val qrBitmap = remember(url) { generateQRCode(context, url) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.shareQrCode) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                qrBitmap?.let { bitmap ->
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "QR Code",
                        modifier = Modifier.size(250.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    qrBitmap?.let { bitmap ->
                        shareQRCode(context, bitmap)
                    }
                    onDismiss()
                }
            ) {
                Text(strings.share)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.cancel)
            }
        }
    )
}

private fun generateQRCode(context: Context, content: String): Bitmap? {
    return try {
        val writer = QRCodeWriter()
        val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, 512, 512)
        val width = bitMatrix.width
        val height = bitMatrix.height
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        for (x in 0 until width) {
            for (y in 0 until height) {
                bitmap.setPixel(
                    x,
                    y,
                    if (bitMatrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE
                )
            }
        }

        // 앱 아이콘을 가져와 QR 코드 중앙에 삽입
        addLogoToQRCode(context, bitmap)
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

private fun addLogoToQRCode(context: Context, qrBitmap: Bitmap): Bitmap {
    return try {
        val iconDrawable = ContextCompat.getDrawable(context, org.parkjw.capylinker.R.mipmap.ic_launcher)
        val iconBitmap = if (iconDrawable is BitmapDrawable) {
            iconDrawable.bitmap
        } else {
            val bitmap = Bitmap.createBitmap(
                iconDrawable?.intrinsicWidth ?: 100,
                iconDrawable?.intrinsicHeight ?: 100,
                Bitmap.Config.ARGB_8888
            )
            val canvas = Canvas(bitmap)
            iconDrawable?.setBounds(0, 0, canvas.width, canvas.height)
            iconDrawable?.draw(canvas)
            bitmap
        }

        // 아이콘을 흑백으로 변환
        val grayscaleIcon = convertToGrayscale(iconBitmap)

        // QR 코드 중앙에 배치할 아이콘 크기 계산 (QR 코드의 약 20%)
        val logoSize = (qrBitmap.width * 0.2).toInt()
        val scaledLogo = Bitmap.createScaledBitmap(grayscaleIcon, logoSize, logoSize, true)

        // 흰색 배경이 있는 로고 생성 (더 잘 보이도록)
        val logoWithBackground = Bitmap.createBitmap(logoSize, logoSize, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(logoWithBackground)

        // 흰색 원형 배경 그리기
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            style = Paint.Style.FILL
        }
        val padding = logoSize * 0.1f
        canvas.drawRoundRect(
            RectF(padding, padding, logoSize - padding, logoSize - padding),
            logoSize * 0.15f,
            logoSize * 0.15f,
            paint
        )

        // 로고 그리기
        val logoPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        canvas.drawBitmap(scaledLogo, 0f, 0f, logoPaint)

        // 최종 QR 코드에 로고 합성
        val combinedBitmap = qrBitmap.copy(Bitmap.Config.ARGB_8888, true)
        val finalCanvas = Canvas(combinedBitmap)

        val left = (qrBitmap.width - logoSize) / 2f
        val top = (qrBitmap.height - logoSize) / 2f
        finalCanvas.drawBitmap(logoWithBackground, left, top, null)

        combinedBitmap
    } catch (e: Exception) {
        e.printStackTrace()
        qrBitmap
    }
}

private fun convertToGrayscale(bitmap: Bitmap): Bitmap {
    val grayscaleBitmap = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(grayscaleBitmap)
    val paint = Paint().apply {
        colorFilter = ColorMatrixColorFilter(ColorMatrix().apply {
            setSaturation(0f)
        })
    }
    canvas.drawBitmap(bitmap, 0f, 0f, paint)
    return grayscaleBitmap
}

private fun shareQRCode(context: Context, bitmap: Bitmap) {
    try {
        val imagesFolder = File(context.cacheDir, "images")
        imagesFolder.mkdirs()
        val file = File(imagesFolder, "qr_code.png")

        val outputStream = FileOutputStream(file)
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
        outputStream.flush()
        outputStream.close()

        val uri = FileProvider.getUriForFile(
            context,
            "${context.applicationContext.packageName}.provider",
            file
        )

        val shareIntent = Intent().apply {
            action = Intent.ACTION_SEND
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        context.startActivity(Intent.createChooser(shareIntent, "QR 코드 공유"))
    } catch (e: Exception) {
        e.printStackTrace()
        Toast.makeText(context, "QR 코드 공유에 실패했습니다.", Toast.LENGTH_SHORT).show()
    }
}
