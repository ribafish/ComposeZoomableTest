package com.example.zoomabletest

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.zoomabletest.ui.theme.ZoomableTestTheme
import com.google.accompanist.pager.ExperimentalPagerApi
import com.google.accompanist.pager.HorizontalPager
import com.google.accompanist.pager.rememberPagerState

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalPagerApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ZoomableTestTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(0.5f)
                        ) {
                            var pagerScrollEnabled by remember { mutableStateOf(true) }
                            HorizontalPager(
                                count = 10,
                                state = rememberPagerState(),
                                modifier = Modifier.fillMaxSize(),
//                                userScrollEnabled = pagerScrollEnabled,
                                itemSpacing = 10.dp,
                            ) { page ->
                                Box(modifier = Modifier.align(Alignment.Center)) {
                                    val zoomableState = ZoomableState(minScale = 1f, maxScale = 20f)
                                    Zoomable(
                                        state = zoomableState,
                                        doubleTapScale = {
                                            if (zoomableState.scale > 1f) zoomableState.minScale else 3F
                                        },
                                        finishDragNotConsumeDirection = ZoomableConsumeDirection.Horizontal,
                                    ) {
//                                    val zoomableState = rememberZoomableState3()
//                                    Zoomable3(
//                                        zoomableState = zoomableState,
//                                        dragGesturesEnabled = {!zoomableState.notTransformed}
//                                    ) {
//                                        pagerScrollEnabled = zoomableState.notTransformed
                                        AsyncImage(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .align(Alignment.Center),
                                            model = ImageRequest.Builder(LocalContext.current)
                                                .data(R.drawable.test_thumbnail).build(),
                                            contentDescription = null,
                                            contentScale = ContentScale.FillWidth,
                                        )
                                    }
                                    Text(text = "Page $page")
                                }
                            }
                            Text(text = "Zoom", modifier = Modifier.align(Alignment.TopEnd))
                        }

                        Box(
                            modifier = Modifier.fillMaxSize()
                        ) {
                            HorizontalPager(
                                count = 10,
                                state = rememberPagerState(),
                                modifier = Modifier.fillMaxSize(),
                                itemSpacing = 10.dp,
                            ) { page ->
                                Box {
                                    AsyncImage(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .align(Alignment.Center),
                                        model = ImageRequest.Builder(LocalContext.current)
                                            .data(R.drawable.test_thumbnail).build(),
                                        contentDescription = null,
                                        contentScale = ContentScale.FillWidth,
                                    )
                                    Text(text = "Page $page")
                                }
                            }
                            Text(text = "No zoom", modifier = Modifier.align(Alignment.TopEnd))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun Img() {

}
