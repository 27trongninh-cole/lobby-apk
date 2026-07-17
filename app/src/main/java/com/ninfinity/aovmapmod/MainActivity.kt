package com.ninfinity.aovmapmod

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chaquo.python.PyObject
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import com.ninfinity.aovmapmod.ui.theme.AOVMapModTheme

class MainActivity : ComponentActivity() {

    // Module Python chứa logic rebuild (tương ứng app.py hiện tại,
    // sẽ được đặt tại app/src/main/python/rebuild_engine.py)
    private lateinit var pyRebuildEngine: PyObject

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Khởi động Python runtime nhúng trong app (Chaquopy) — chỉ init 1 lần
        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(this))
        }
        val py = Python.getInstance()
        // TODO: đổi "rebuild_engine" thành tên module Python thật sau khi
        // chuyển logic từ app.py sang dạng hàm gọi được (không dùng Flask route nữa)
        // pyRebuildEngine = py.getModule("rebuild_engine")

        setContent {
            AOVMapModTheme {
                HomeScreen()
            }
        }
    }
}

@Composable
fun HomeScreen() {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "AOV Map Mod",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                IconButton(onClick = { /* TODO: settings */ }) {
                    Icon(Icons.Filled.Build, contentDescription = "Cài đặt")
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Chọn setting đồ họa để chỉnh texture",
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                fontSize = 14.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            LevelSelector()

            Spacer(modifier = Modifier.height(24.dp))

            // Placeholder — lưới 16 ô nền map sẽ thay bằng LazyVerticalGrid
            // load ảnh qua Coil từ URL Supabase Storage ở bước tiếp theo
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "🗺️ Lưới nền map (16 mảnh) sẽ hiển thị ở đây",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            }
        }
    }
}

@Composable
fun LevelSelector() {
    val levels = listOf("Mượt/Thấp", "Trung bình", "Cao", "HD")
    var selected by remember { mutableStateOf(0) }

    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        levels.forEachIndexed { index, label ->
            val isSelected = selected == index
            FilterChip(
                selected = isSelected,
                onClick = { selected = index },
                label = { Text(label) }
            )
        }
    }
}
