package com.osagem.photoview4rokidglasses

import android.Manifest
import android.content.ContentUris
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.bumptech.glide.Glide
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import androidx.media3.common.MediaItem as ExoMediaItem
import kotlinx.coroutines.launch
import android.os.Handler
import android.os.Looper
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.Player
import android.view.WindowManager
import android.provider.Settings
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat

class PhotoListActivity : AppCompatActivity() {

    data class MediaItem(
        val uri: Uri,
        val type: MediaType,
        val dateTaken: Long,
        val displayName: String
    )
    enum class MediaType { IMAGE, VIDEO }
    companion object {
        private const val DEBUG = true //false or true 调试开关：上线时改为 false 即可关闭所有调试日志

        private const val LEGACY_STORAGE_PERMISSION_REQUEST_CODE = 102

        private const val TAG = "PhotoManager"

        private fun debugLog(message: String) {
            if (DEBUG) Log.d(TAG, message)
        }
    }

    // UI 控件
    private lateinit var latestImageView: ImageView
    private lateinit var latestVideoView: PlayerView
    private lateinit var buttonBackmain: MaterialButton
    private lateinit var buttonDelphoto: MaterialButton
    private lateinit var buttonNext: MaterialButton
    private lateinit var photoCountTextView: TextView

    // 播放器和数据
    private var exoPlayer: ExoPlayer? = null
    private var allMediaItems = mutableListOf<MediaItem>()
    private var currentImageIndex = -1

    // 工具类
    private var centeredToast: Toast? = null
    private var emojiBitmap: Bitmap? = null
    private lateinit var loadingIndicator: ProgressBar
    private lateinit var textView_videoInfo: TextView //增加文件信息显示

    // 用于定时更新视频时长播放进度信息的 Handler 和 Runnable
    private val handler = Handler(Looper.getMainLooper())
    private val updateProgressAction = object : Runnable {
        override fun run() {
            exoPlayer?.let { player ->
                // 只要播放器不是空闲状态且有时长，就更新UI
                if (player.playbackState != Player.STATE_IDLE && player.duration > 0) {
                    val currentPosition = player.currentPosition
                    val totalDuration = player.duration
                    textView_videoInfo.text = getString(
                        R.string.video_info_format,
                        formatDuration(currentPosition),
                        formatDuration(totalDuration)
                    )
                }
            }
            // 每秒钟重复执行此任务
            handler.postDelayed(this, 1000)
        }
    }

    // ------------------- 生命周期管理-------------------
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_photo_list)

        // 屏幕常亮
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // 让内容布局扩展到系统栏（状态栏和导航栏）后面
        WindowCompat.setDecorFitsSystemWindows(window, false)
        // 获取 WindowInsetsController
        val windowInsetsController =
            WindowCompat.getInsetsController(window, window.decorView) ?: return
        // 隐藏状态栏和导航栏
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
        // 设置交互行为：当从屏幕边缘滑动时，系统栏会短暂显示
        windowInsetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        // --- 结束全屏代码 ---

        // 设置窗口
        setupWindowInsets()

        // 初始化视图
        initializeViews()

        // 在 onCreate 中只调用一次，创建播放器实例
        // 初始化播放器 这是播放器生命周期的起点
        initializePlayer()

        // 步骤 2: 在加载数据前检查权限
        if (checkStoragePermission()) {
            loadAllMediaUris()
        } else {
            requestStoragePermission()
        }

        // 设置监听器等
        setupListeners()

        // 开始业务逻辑
        updatePhotoCountText()
        emojiBitmap = createBitmapFromEmoji("🤷", 200)
    }

    private fun checkStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11 (R) 及以上版本
            Environment.isExternalStorageManager()
        } else {
            // Android 10 (Q) 及以下版本
            ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // 引导用户到设置页面授予 MANAGE_EXTERNAL_STORAGE
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                intent.data = Uri.parse("package:$packageName")
                storagePermissionLauncher.launch(intent)
            } catch (e: Exception) {
                val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                storagePermissionLauncher.launch(intent)
            }
        } else {
            // 在旧版本上请求 WRITE_EXTERNAL_STORAGE
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                LEGACY_STORAGE_PERMISSION_REQUEST_CODE
            )
        }
    }

    // 处理从设置页面返回的结果
    private val storagePermissionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            // 当用户从设置页返回后，再次检查权限
            if (checkStoragePermission()) {
                loadAllMediaUris()
            } else {
                showCenteredToast("未授予文件管理权限，无法加载照片")
                handleNoPhotosFound()
            }
        }

    // 处理旧版安卓的权限请求回调
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LEGACY_STORAGE_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                loadAllMediaUris()
            } else {
                showCenteredToast("未授予存储权限，无法加载照片")
                handleNoPhotosFound()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // 绑定 PlayerView 和 ExoPlayer 这会创建视频渲染所需的 Surface
        latestVideoView.player = exoPlayer
    }

    override fun onResume() {
        super.onResume()
        if (latestVideoView.isVisible && exoPlayer?.isPlaying == false) {
            exoPlayer?.play()
        }
    }

    override fun onPause() {
        super.onPause()
        // 统一在在此暂停播放，以节省资源。
        exoPlayer?.pause()
    }

    override fun onStop() {
        super.onStop()
        // 解除 PlayerView 和 ExoPlayer 的绑定 安全地释放 Surface，避免资源泄露和状态冲突
        latestVideoView.player = null
    }

    override fun onDestroy() {
        super.onDestroy()
        // 停止所有待处理的进度更新任务
        handler.removeCallbacks(updateProgressAction)
        // 在 onDestroy 中彻底释放播放器资源 这是播放器生命周期的终点
        releasePlayer()
        centeredToast?.cancel()
        emojiBitmap?.recycle()
        emojiBitmap = null
    }

    // ------------------- 播放器初始化与释放 -------------------
    private fun initializePlayer() {
        // 这个方法现在只在 onCreate 中被调用一次
        // 它只负责创建实例，不涉及UI绑定
        exoPlayer = ExoPlayer.Builder(this).build().apply {
            repeatMode = ExoPlayer.REPEAT_MODE_ONE
            // 添加监听器以在视频准备就绪时更新UI
            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    // 当播放器准备好时
                    if (playbackState == Player.STATE_READY) {
                        val duration = exoPlayer?.duration ?: 0
                        // 只要播放器获得了有效的时长，就更新
                        if (duration > 0) {
                            textView_videoInfo.text = getString(
                                R.string.video_info_format,
                                formatDuration(0),
                                formatDuration(duration)
                            )
                        }
                    }

                    // 【新增逻辑】如果媒体播放结束或者播放器停止，我们也需要清空文本
                    // 这能确保从视频切换到图片时，信息能被正确清除
                    if (playbackState == Player.STATE_ENDED || playbackState == Player.STATE_IDLE) {
                        if (latestImageView.isVisible) { // 确认当前是图片视图在显示
                            textView_videoInfo.text = ""
                        }
                    }
                }
            })
        }
    }

    private fun releasePlayer() {
        // 这个方法现在只在 onDestroy 中被调用。
        // 在释放播放器本身之前，先从视图解绑。
        latestVideoView.player = null
        exoPlayer?.release()
        exoPlayer = null
    }

    // ------------------- 媒体加载与切换 -------------------
    private fun loadSpecificMedia(index: Int) {
        if (index !in allMediaItems.indices) {
            handleNoPhotosFound()
            return
        }
        currentImageIndex = index
        val item = allMediaItems[index]
        debugLog("Displaying ${item.type.name} → ${item.uri}")

        // 移除之前的所有定时任务，防止重复更新
        handler.removeCallbacks(updateProgressAction)

        // 统一管理视图可见性和播放器状态
        when (item.type) {
            MediaType.VIDEO -> {
                // 准备播放视频
                latestImageView.visibility = View.INVISIBLE
                latestVideoView.visibility = View.VISIBLE

                // 当是视频时，显示视频时长信息文本框
                textView_videoInfo.visibility = View.VISIBLE
                // 视频时长信息未完成加载时的占位符
                textView_videoInfo.text = "..."

                // 确保PlayerView与播放器绑定。ExoPlayer将自动处理Surface的获取。
                if (latestVideoView.player == null) {
                    latestVideoView.player = exoPlayer
                }

                // 使用ExoPlayer的高效媒体项切换API
                val mediaItem = ExoMediaItem.fromUri(item.uri)
                exoPlayer?.setMediaItem(mediaItem)
                exoPlayer?.prepare() // 准备新的媒体项
                exoPlayer?.play()     // 开始或恢复播放

                // 启动进度更新的定时任务
                handler.post(updateProgressAction)
                debugLog("Playing video and starting progress updates.")
            }
            MediaType.IMAGE -> {
                // 当是图片时，隐藏信息文本框
                textView_videoInfo.visibility = View.INVISIBLE
                textView_videoInfo.text = "" // 同时清空文本

                // 停止播放并从PlayerView解绑，这是关键！
                // 这会干净地释放Surface，避免资源冲突。
                exoPlayer?.stop() // 停止播放
                latestVideoView.player = null // 解绑

                // 准备显示图片
                latestVideoView.visibility = View.INVISIBLE
                latestImageView.visibility = View.VISIBLE

                // 加载图片
                Glide.with(this)
                    .load(item.uri)
                    .into(latestImageView)
                debugLog("Displaying image.")
            }
        }
        updatePhotoCountText()
    }

    // ------------------- 其他辅助方法 -------------------
    private fun formatDuration(milliseconds: Long): String {
        val totalSeconds = milliseconds / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format("%02d:%02d", minutes, seconds)
    }

    private fun initializeViews() {
        latestImageView = findViewById(R.id.latestImageView)
        latestVideoView = findViewById(R.id.playerView)
        buttonNext = findViewById(R.id.buttonNext)
        buttonBackmain = findViewById(R.id.buttonBackmain)
        buttonBackmain.visibility = View.VISIBLE
        buttonDelphoto = findViewById(R.id.buttonDelphoto)
        photoCountTextView = findViewById(R.id.photoCountTextView)
        loadingIndicator = findViewById(R.id.loadingIndicator)
        textView_videoInfo = findViewById(R.id.textView_videoInfo) //增加文件信息显示 初始化
    }

    // 视频加载耗时等待时的加载指示器
    private fun showLoadingIndicator(show: Boolean) {
        loadingIndicator.visibility = if (show) View.VISIBLE else View.GONE
    }

    // 查看窗口获得焦点时，请求next按钮获取焦点，改进体验
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            buttonNext.requestFocus()
        }
    }

    private fun setupListeners() {

        // 为“下一个”按钮设置点击事件
        buttonNext.setOnClickListener { loadNextMedia() }

        // 为“返回主页”按钮设置点击事件
        buttonBackmain.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            startActivity(intent)
            finish()
        }

        // 为“删除”按钮设置点击事件
        buttonDelphoto.setOnClickListener {
            if (allMediaItems.isNotEmpty() && currentImageIndex in allMediaItems.indices) {
//                deleteCurrentImage()
                showDeleteConfirmationDialog()
            } else {
                showCenteredToast(getString(R.string.toast_no_photo_selected_to_del))
            }
        }
    }

    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    // 显示删除确认弹窗
    private fun showDeleteConfirmationDialog() {
        val builder = AlertDialog.Builder(this, R.style.CustomAlertDialogTheme)
        // 加载自定义标题布局文件
        val customTitleView = layoutInflater.inflate(R.layout.dialog_custom_title, null)

        // 通过ID找到布局中的TextView
        val titleTextView = customTitleView.findViewById<TextView>(R.id.dialog_del_confirm_title_text)

        // 为自定义标题设置文本内容
        titleTextView.text = getString(R.string.dialog_del_confirm_text)

        // 将包含自定义TextView的整个视图设置为弹窗的标题
        builder.setCustomTitle(customTitleView)

        // 确认删除按钮
        builder.setPositiveButton(getString(R.string.button_delPhoto)) { dialog, _ ->
            deleteCurrentImage()
            dialog.dismiss()
        }

        // 取消删除按钮
        builder.setNegativeButton(getString(R.string.button_delCancel)) { dialog, _ ->
            dialog.dismiss()
        }

        val dialog = builder.create()

        // 确保弹窗时也能隐藏系统导航栏和状态栏，保持沉浸式体验
        dialog.window?.let { window ->
            // WindowInsetsControllerCompat 是 AndroidX 中用于控制系统栏的推荐方式
            val controller = WindowInsetsControllerCompat(window, window.decorView)
            // 隐藏状态栏和导航栏
            controller.hide(WindowInsetsCompat.Type.systemBars())
            // 设置行为模式为 BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE，
            // 这样即使用户从屏幕边缘滑入，系统栏也只是短暂显示然后自动隐藏，
            // 不会破坏应用的沉浸式布局。
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        // 让“删除”按钮（PositiveButton）默认获得焦点
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).requestFocus()
        }

        dialog.show() // 显示弹窗
    }

    private fun deleteCurrentImage() {
        if (currentImageIndex == -1 || allMediaItems.isEmpty()) {
            debugLog("Deletion failed: Invalid index or empty list.")
            return
        }

        val itemToDelete = allMediaItems[currentImageIndex]

        lifecycleScope.launch {
            var fileDeleted = false

            // 在后台线程执行文件 IO 操作
            withContext(Dispatchers.IO) {
                val path = getPathFromUri(itemToDelete.uri)
                if (path != null) {
                    val mediaFile = File(path)
                    if (mediaFile.exists() && mediaFile.delete()) {
                        fileDeleted = true
                        debugLog("Successfully deleted media file: ${mediaFile.absolutePath}")

                        // 删除成功后，通知 MediaStore 更新
                        scanFilePath(mediaFile.absolutePath)

                        // 如果是视频，尝试删除同名的 .txt 文件
                        if (itemToDelete.type == MediaType.VIDEO) {
                            val txtFile = File(mediaFile.parent, "${mediaFile.nameWithoutExtension}.txt")
                            if (txtFile.exists() && txtFile.delete()) {
                                debugLog("Successfully deleted associated txt file: ${txtFile.absolutePath}")
                                // 同样通知 MediaStore 更新
                                scanFilePath(txtFile.absolutePath)
                            } else {
                                debugLog("Associated txt file not found or failed to delete: ${txtFile.absolutePath}")
                            }
                        }
                    } else {
                        Log.e(TAG, "Failed to delete media file: ${mediaFile.absolutePath}")
                    }
                } else {
                    Log.e(TAG, "Could not get path from Uri to delete file: ${itemToDelete.uri}")
                }
            }

            // 回到主线程更新 UI
            if (fileDeleted) {
                showCenteredToast(getString(R.string.toast_photo_deleted_succe))
                exoPlayer?.stop()

                allMediaItems.remove(itemToDelete)

                if (allMediaItems.isEmpty()) {
                    handleNoPhotosFound()
                } else {
                    if (currentImageIndex >= allMediaItems.size) {
                        currentImageIndex = allMediaItems.size - 1
                    }
                    loadSpecificMedia(currentImageIndex)
                }
                updatePhotoCountText()
            } else {
                showCenteredToast(getString(R.string.toast_failed_to_delete_photo))
            }
        }
    }

    // 辅助函数：从 Uri 获取文件路径
    private fun getPathFromUri(uri: Uri): String? {
        // MediaStore.MediaColumns.DATA 在 Images 和 Video Provider 中是通用的列名
        // 并且在 API 29 以下是标准做法。 在 API 29+ 不推荐，但通常仍可查询到。
        val projection = arrayOf(MediaStore.MediaColumns.DATA)
        try {
            contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val columnIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA)
                    return cursor.getString(columnIndex)
                }
            }
        } catch (e: Exception) {
            // 如果查询失败（例如，URI无效或权限问题），记录错误
            Log.e(TAG, "Failed to get path from URI: $uri", e)
        }
        // 如果查询没有返回结果或发生异常，则返回 null
        return null
    }


    // 辅助函数：通知 MediaStore 文件已被删除
    private fun scanFilePath(path: String) {
        MediaScannerConnection.scanFile(applicationContext, arrayOf(path), null, null)
    }

    private fun loadAllMediaUris() {
        // 使用 lifecycleScope 启动一个协程，它会自动在 Activity 销毁时取消
        lifecycleScope.launch {
            // 显示一个加载指示器（可选，但推荐）
            showLoadingIndicator(true)
            val mediaResult = withContext(Dispatchers.IO) {
                val imageItems = queryMedia(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    Environment.DIRECTORY_DCIM + File.separator + "Camera",
                    MediaType.IMAGE
                )
                val picturesItems = queryMedia(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    Environment.DIRECTORY_PICTURES,
                    MediaType.IMAGE
                )
                val videoItems = queryMedia(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    Environment.DIRECTORY_MOVIES + File.separator + "Camera",
                    MediaType.VIDEO
                )
                val videoBItems = queryMedia(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    Environment.DIRECTORY_PICTURES,
                    MediaType.VIDEO
                )
                val videoCItems = queryMedia(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    Environment.DIRECTORY_MOVIES,
                    MediaType.VIDEO
                )
                val videoDItems = queryMedia(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    Environment.DIRECTORY_DCIM + File.separator + "Camera",
                    MediaType.VIDEO
                )
                // 在后台合并并排序
                (imageItems + picturesItems + videoItems + videoBItems + videoCItems + videoDItems).sortedByDescending { it.dateTaken }
            }
            // 隐藏加载指示器
            showLoadingIndicator(false)
            // withContext 会自动切回主线程，在这里安全地更新UI
            // showLoadingIndicator(false)
            allMediaItems.clear()
            allMediaItems.addAll(mediaResult)
            currentImageIndex = -1 // 重置索引

            if (allMediaItems.isNotEmpty()) {
                debugLog("Total media loaded: ${allMediaItems.size}")
                //loadNextMedia()
                loadSpecificMedia(0)
                buttonNext.visibility = View.VISIBLE
            } else {
                debugLog("No media found in specified directories")
                handleNoPhotosFound()
            }
            // 确保在加载完成后更新计数
            updatePhotoCountText()
        }
    }

    // queryMedia 只负责查询并返回结果列表
    private fun queryMedia(contentUri: Uri, folder: String, type: MediaType): List<MediaItem> {
        val projection: Array<String>
        val selection: String
        val selectionArgs: Array<String>

        // Android Q 及以上版本的路径查询逻辑
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            projection = arrayOf(
                MediaStore.MediaColumns._ID,
                MediaStore.MediaColumns.DATE_TAKEN,
                MediaStore.MediaColumns.DISPLAY_NAME
            )
            // 在 Android 10+，直接使用 RELATIVE_PATH 查询更高效、更标准
            selection = "${MediaStore.MediaColumns.RELATIVE_PATH} = ?"
            selectionArgs = arrayOf("$folder/") // 精确匹配，而不是使用 LIKE
        } else {
            projection = arrayOf(
                MediaStore.MediaColumns._ID,
                MediaStore.MediaColumns.DATE_TAKEN,
                MediaStore.MediaColumns.DATA,
                MediaStore.MediaColumns.DISPLAY_NAME
            )
            selection = "${MediaStore.MediaColumns.DATA} LIKE ?"
            selectionArgs = arrayOf("%/$folder/%") // 旧版本只能通过模糊匹配文件路径
        }

        // 统一的排序顺序
        val sortOrder = "${MediaStore.MediaColumns.DATE_TAKEN} DESC"

        return try {
            // 使用'use'块来自动管理Cursor的生命周期，并在结束后返回列表
            contentResolver.query(contentUri, projection, selection, selectionArgs, sortOrder)?.use { cursor ->
                val items = mutableListOf<MediaItem>()
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val dateTakenColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_TAKEN)
                val displayNameColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val dateTaken = cursor.getLong(dateTakenColumn)
                    val displayName = cursor.getString(displayNameColumn)
                    val uri = ContentUris.withAppendedId(contentUri, id)
                    items.add(MediaItem(uri, type, dateTaken, displayName))
                }
                debugLog("Query found ${items.size} items of type ${type.name} in $folder")
                items // use块的最后一行作为其返回值
            } ?: emptyList() // 如果查询返回null，则直接返回一个空列表
        } catch (e: Exception) {
            Log.e(TAG, "Error loading ${type.name} from $folder. This might be a permission issue.", e)
            emptyList() // 如果发生异常，同样返回一个空列表，保证程序不会崩溃
        }
    }

    private fun loadNextMedia() {
        if (allMediaItems.isEmpty()) {
            handleNoPhotosFound()
            return
        }
        currentImageIndex++
        if (currentImageIndex >= allMediaItems.size) currentImageIndex = 0
        loadSpecificMedia(currentImageIndex)
    }

    private fun updatePhotoCountText() {
        val currentNumber = if (currentImageIndex >= 0) currentImageIndex + 1 else 0
        val totalNumber = allMediaItems.size
        photoCountTextView.text = getString(R.string.photo_count_format, currentNumber, totalNumber)
        photoCountTextView.visibility = View.VISIBLE
        buttonDelphoto.visibility = if (totalNumber > 0) View.VISIBLE else View.GONE
        buttonNext.visibility = if (totalNumber > 1) View.VISIBLE else View.GONE
    }

    private fun handleNoPhotosFound(isError: Boolean = false) {
        val message =
            if (isError) getString(R.string.toast_error_accessing_photos) else getString(R.string.toast_no_photos_found)
        showCenteredToast(message, Toast.LENGTH_LONG)
        allMediaItems.clear()
        currentImageIndex = -1
        //exoPlayer?.stop()
        latestVideoView.visibility = View.INVISIBLE
        latestImageView.visibility = View.VISIBLE
        val emojiBitmapToShow =
            emojiBitmap ?: createBitmapFromEmoji("🤷", 200).also { emojiBitmap = it }
        latestImageView.setImageBitmap(emojiBitmapToShow)
        updatePhotoCountText()
        buttonBackmain.visibility = View.VISIBLE
    }

    private fun showCenteredToast(message: String, duration: Int = Toast.LENGTH_SHORT) {
        centeredToast?.cancel()
        centeredToast = Toast.makeText(this, message, duration).apply {
            setGravity(android.view.Gravity.CENTER, 0, 120)
            show()
        }
    }

    private fun createBitmapFromEmoji(emojiString: String, size: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size * 0.25f
            color = Color.BLACK
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT
        }
        val x = canvas.width / 2f
        val y = canvas.height / 2f - (paint.descent() + paint.ascent()) / 2f
        canvas.drawText(emojiString, x, y, paint)
        return bitmap
    }

}
