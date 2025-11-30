package com.osagem.photodel4rokidglasses

import android.Manifest
import android.content.ContentUris
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import android.view.View
import android.view.ViewPropertyAnimator
import android.view.WindowManager
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem as ExoMediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.bumptech.glide.Glide
import com.google.android.material.button.MaterialButton
import java.io.File
import java.io.FileNotFoundException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PhotoListActivity : AppCompatActivity() {
    // =============================================================================================
    // 静态成员与常量 (Companion Object & Constants)
    // =============================================================================================
    companion object {
        private const val DEBUG = false //false 关闭所有调试日志，与下面一行debug同时只开一个
//        private const val DEBUG = true //true 打开所有调试日志，快捷键快速注释与反注释来启用禁用，偷懒用；-）
        private const val LEGACY_STORAGE_PERMISSION_REQUEST_CODE = 102
        private const val TAG = "PhotoManager"
        private fun debugLog(message: String) {
            if (DEBUG) Log.d(TAG, message)
        }
    }

    // =============================================================================================
    // 数据模型 (Data Models)
    // =============================================================================================
    data class MediaItem(
        val uri: Uri,
        val type: Type,
        val dateTaken: Long,
        val displayName: String
    )   {
        enum class Type { IMAGE, VIDEO }
        }

    // =============================================================================================
    // 属性声明 (Properties)
    // =============================================================================================

    // --- UI 控件 (UI Views)
    private lateinit var latestImageView: ImageView
    private lateinit var latestVideoView: PlayerView
    private lateinit var buttonBackmain: MaterialButton
    private lateinit var buttonDelphoto: MaterialButton
    private lateinit var buttonNext: MaterialButton
    private lateinit var photoCountTextView: TextView
    private lateinit var loadingIndicator: ProgressBar
    private lateinit var textViewVideoInfo: TextView

    // --- 数据与状态 (Data & State)
    private var allMediaItems = mutableListOf<MediaItem>()
    private var currentImageIndex = -1

    // --- 播放器与动画 (Player & Animation)
    private var exoPlayer: ExoPlayer? = null
    private var flipAnimator: ViewPropertyAnimator? = null

    // --- 工具类与处理器 (Utilities & Handlers)
    private var centeredToast: Toast? = null
    private val handler = Handler(Looper.getMainLooper())
    private val updateProgressAction = object : Runnable {
        override fun run() {
            exoPlayer?.let { player ->
                // 当播放器正在播放或暂停时（非空闲状态）并且视频有时长，才更新UI
                if (player.playbackState != Player.STATE_IDLE && player.duration > 0) {
                    val currentPosition = player.currentPosition
                    val totalDuration = player.duration
                    // 更新UI，显示 "当前时间 / 总时长"
                    textViewVideoInfo.text = getString(
                        R.string.video_play_info_format,
                        formatDuration(currentPosition),
                        formatDuration(totalDuration)
                    )
                }
            }
            // 在 run() 方法的末尾，再次将自身（这个Runnable）提交到Handler队列，形成一个线性的、安全的循环
            handler.postDelayed(this, 1000)
        }
    }

    // --- 权限处理回调 (Permission Handling Callbacks)
    private val storagePermissionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            // 当用户从设置页返回后，再次检查权限
            lifecycleScope.launch {
                if (checkStoragePermission()) {
                    // 用户授予了权限，在后台开始加载
                    withContext(Dispatchers.IO) {
                        loadAllMediaUris()
                    }
                } else {
                    // 用户未授予权限
                    showLoadingIndicator(false) // 隐藏加载动画
                    showCenteredToast(getString(R.string.toast_permissions_not_granted_cannot_load_media))
                    handleNoPhotosFound()
                }
            }
        }

    // =============================================================================================
    // Activity 生命周期方法 (Lifecycle Methods)
    // =============================================================================================
    override fun onCreate(savedInstanceState: Bundle?) {
        // 调用父类实现
        super.onCreate(savedInstanceState)

        // 窗口和屏幕设置，沉浸式与全屏显示设置
        enableEdgeToEdge() // 沉浸式与全屏显示
        WindowCompat.setDecorFitsSystemWindows(window, false) // 设置为 false，应用布局会被允许绘制到整个屏幕区域，包括系统栏的后面
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) // 设置屏幕常亮

        // 隐藏系统栏并设置其行为
        WindowCompat.getInsetsController(window, window.decorView)?.let { windowInsetsController ->
            windowInsetsController.hide(WindowInsetsCompat.Type.systemBars()) // 隐藏状态栏（顶部显示时间、电量）和导航栏（底部显示返回、主页按钮）
            windowInsetsController.systemBarsBehavior = // 设置当系统栏被隐藏后的交互行为：当从屏幕边缘滑动时，系统栏会短暂显示
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        // 设置布局(必须在任何视图操作之前)
        setContentView(R.layout.activity_photo_list)

        // UI和业务逻辑初始化
        initializeViews() // 初始化视图引用
        initializePlayer() // 初始化播放器
        setupListeners() // 为视图设置监听器

        // 开始加载数据
        showLoadingIndicator(true) // 立即显示加载指示器，让用户感知到正在加载
        updatePhotoCountText() // 显示一个初始状态

        // 在后台协程中检查权限并加载媒体
        lifecycleScope.launch(Dispatchers.Main) {
            if (checkStoragePermission()) {
                // 如果已有权限，直接在后台加载
                withContext(Dispatchers.IO) {
                    loadAllMediaUris()
                }
            } else {
                // 如果没有权限，请求权限。结果将在回调中处理
                requestStoragePermission()
                // 此时保持加载指示器可见，直到权限结果返回
            }
        }

        // 设置窗口
        setupWindowInsets()
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
        // 在这里停止动画
        flipAnimator?.cancel()
        // 停止所有待处理的进度更新任务
        handler.removeCallbacks(updateProgressAction)
        // 在 onDestroy 中彻底释放播放器资源 这是播放器生命周期的终点
        releasePlayer()
        centeredToast?.cancel()
        // 明确让 Glide 清理其与此 Activity 相关的资源
        if (!isDestroyed) { // 确保 Activity 尚未完全销毁
            Glide.with(this).onDestroy()
        }
    }

    // =============================================================================================
    // 权限处理 (Permission Handling)
    // =============================================================================================
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LEGACY_STORAGE_PERMISSION_REQUEST_CODE) {
            lifecycleScope.launch {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // 用户授予了权限，在后台开始加载
                    withContext(Dispatchers.IO) {
                        loadAllMediaUris()
                    }
                } else {
                    // 用户未授予权限
                    showLoadingIndicator(false) // 隐藏加载动画
                    showCenteredToast(getString(R.string.toast_permissions_not_granted_cannot_load_media))
                    handleNoPhotosFound()
                }
            }
        }
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

    // =============================================================================================
    // 初始化方法 (Initialization Methods)
    // =============================================================================================
    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
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
        textViewVideoInfo = findViewById(R.id.textViewVideoInfo) //增加文件信息显示 初始化
    }
    private fun initializePlayer() {
        // 这个方法现在只在 onCreate 中被调用一次，它只负责创建实例，不涉及UI绑定
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
                            textViewVideoInfo.text = getString(
                                R.string.video_play_info_format,
                                formatDuration(0),
                                formatDuration(duration)
                            )
                        }
                    }

                    // 如果媒体播放结束或者播放器停止，我们也需要清空文本
                    // 这能确保从视频切换到图片时，信息能被正确清除
                    if (playbackState == Player.STATE_ENDED || playbackState == Player.STATE_IDLE) {
                        if (latestImageView.isVisible) { // 确认当前是图片视图在显示
                            textViewVideoInfo.text = ""
                        }
                    }
                }
            })
        }
    }
    private fun setupListeners() {

        // 为“下一个”按钮设置点击事件
        buttonNext.setOnClickListener {
            loadNextMedia()
        }

        // 为“返回”按钮设置点击事件
        buttonBackmain.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            startActivity(intent)
            finish()
        }

        // 为“删除”按钮设置点击事件
        buttonDelphoto.setOnClickListener {
            if (allMediaItems.isNotEmpty() && currentImageIndex in allMediaItems.indices) {
                showDeleteConfirmationDialog()
            } else {
                showCenteredToast(getString(R.string.toast_no_photo_selected_to_del))
            }
        }
    }

    // =============================================================================================
    // 核心业务逻辑 (Core Business Logic)
    // =============================================================================================

    // --- 数据加载 (Data Loading) ---
    private fun loadAllMediaUris() {
        // 使用 lifecycleScope 启动一个协程，它会自动在 Activity 销毁时取消
        lifecycleScope.launch {
            // 显示一个加载指示器
            showLoadingIndicator(true)

            val mediaResult = withContext(Dispatchers.IO) {
                // 在后台线程执行所有媒体查询和排序操作

                // 查询所有相关目录下的图片
                val imageItems = queryMedia(
                    MediaItem.Type.IMAGE,
                    // 传入所需目录列表
                    listOf(
                        Environment.DIRECTORY_DCIM,
                        Environment.DIRECTORY_PICTURES
                    )
                )

                // 查询所有相关目录下的视频
                val videoItems = queryMedia(
                    MediaItem.Type.VIDEO,
                    // 传入所需目录列表
                    listOf(
                        Environment.DIRECTORY_DCIM,
                        Environment.DIRECTORY_PICTURES,
                        Environment.DIRECTORY_MOVIES
                    )
                )

                // 在后台合并并按日期降序排序
                val allItems = (imageItems + videoItems).sortedByDescending { it.dateTaken }
                // 在返回结果前，过滤掉所有无效的 Uri，这样做可以确保UI层永远不会接收到指向已删除文件的媒体项
                allItems.filter { item -> isUriValid(item.uri) }
            }

            // 隐藏加载指示器
            showLoadingIndicator(false)

            // withContext 会自动切回主线程，在这里安全地更新UI
            allMediaItems.clear()
            allMediaItems.addAll(mediaResult)
            currentImageIndex = -1 // 重置索引

            if (allMediaItems.isNotEmpty()) {
                debugLog("已加载所有媒体: ${allMediaItems.size}")
                loadSpecificMedia(0, focusNextButton = true) // 首次加载时请求焦点
                buttonNext.visibility = View.VISIBLE
            } else {
                debugLog("在指定目录没有找到媒体文件")
                handleNoPhotosFound()
            }
            // 确保在加载完成后更新计数
            updatePhotoCountText()
        }
    }
    private fun queryMedia(type: MediaItem.Type, directories: List<String>): List<MediaItem> {
        val contentUri = when (type) {
            MediaItem.Type.IMAGE -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            MediaItem.Type.VIDEO -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }

        val projection: Array<String>
        val selection: String
        val selectionArgs: Array<String>

        // 根据 Android 版本构建查询语句
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            projection = arrayOf(
                MediaStore.MediaColumns._ID,
                MediaStore.MediaColumns.DATE_TAKEN,
                MediaStore.MediaColumns.DISPLAY_NAME
            )
            // 对于 Android 10+, 我们需要匹配指定目录及其所有子目录。
            // 因此使用 LIKE 进行前缀匹配，而不是 IN 精确匹配。
            // 构建 'RELATIVE_PATH LIKE ? OR RELATIVE_PATH LIKE ? OR ...' 语句
            selection = directories.joinToString(" OR ") { "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?" }
            // 为每个 '?' 占位符提供值，确保路径以'/'结尾，并使用'%'通配符匹配后续所有字符。
            // 例如：'DCIM/%' 可以匹配 'DCIM/Camera/'、'DCIM/Screenshots/' 等。
            selectionArgs = directories.map { "$it/%" }.toTypedArray()
        } else {
            projection = arrayOf(
                MediaStore.MediaColumns._ID,
                MediaStore.MediaColumns.DATE_TAKEN,
                MediaStore.MediaColumns.DATA, // 旧版本需要 DATA 列
                MediaStore.MediaColumns.DISPLAY_NAME
            )
            // 旧版本通过 LIKE 模糊匹配文件完整路径，这部分逻辑是正确的，保持不变。
            // 构建 'DATA LIKE ? OR DATA LIKE ? OR ...' 语句
            selection = directories.joinToString(" OR ") { "${MediaStore.MediaColumns.DATA} LIKE ?" }
            // 为每个 '?' 占位符提供值
            selectionArgs = directories.map { "%/$it/%" }.toTypedArray()
        }

        val sortOrder = "${MediaStore.MediaColumns.DATE_TAKEN} DESC"
        val items = mutableListOf<MediaItem>()

        try {
            contentResolver.query(contentUri, projection, selection, selectionArgs, sortOrder)?.use { cursor ->
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
//                debugLog("Query for ${type.name} in [${directories.joinToString()}] found ${items.size} items.")
                debugLog("从 [${directories.joinToString()}] 查找到 ${items.size} 个 ${type.name} ")
            }
        } catch (e: Exception) {
//            debugLog("Error loading ${type.name} from [${directories.joinToString()}]. Might be a permission issue. Error: ${e.message}")
            debugLog("从 [${directories.joinToString()}] 加载 ${type.name} 出错，可能是权限问题导致。 Error: ${e.message}")
            // 发生异常时返回空列表，保证程序健壮性
            return emptyList()
        }
        return items
    }

    // --- 媒体控制 (Media Control) ---
    private fun loadNextMedia() {
        if (allMediaItems.isEmpty()) {
            handleNoPhotosFound()
            return
        }
        currentImageIndex++
        if (currentImageIndex >= allMediaItems.size) currentImageIndex = 0
        loadSpecificMedia(currentImageIndex)
    }
    private fun loadSpecificMedia(index: Int, focusNextButton: Boolean = true) {
        if (index !in allMediaItems.indices) {
            handleNoPhotosFound()
            return
        }

        // 在这里停止动画
        flipAnimator?.cancel()
        latestImageView.scaleX = 1f // 将视图恢复到原始状态

        currentImageIndex = index
        val item = allMediaItems[index]
        debugLog("正在显示 ${item.type.name} → ${item.uri}")

        // 移除之前的所有定时任务，防止重复更新
        handler.removeCallbacks(updateProgressAction)

        // 统一管理视图可见性和播放器状态
        when (item.type) {
            MediaItem.Type.VIDEO -> {
                // 准备播放视频
                latestImageView.visibility = View.INVISIBLE
                latestVideoView.visibility = View.VISIBLE

                // 当是视频时，显示视频时长信息文本框
                textViewVideoInfo.visibility = View.VISIBLE
                // 视频时长信息未完成加载时的占位符
                textViewVideoInfo.text = "..."

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
                debugLog("开始播放视频，并显示进度")
            }
            MediaItem.Type.IMAGE -> {
                // 当是图片时，隐藏信息文本框
                textViewVideoInfo.visibility = View.INVISIBLE
                textViewVideoInfo.text = "" // 同时清空文本

                // 停止播放并从PlayerView解绑，这会干净地释放Surface，避免资源冲突。
                exoPlayer?.stop() // 停止播放
                latestVideoView.player = null // 解绑

                // 准备显示图片
                latestVideoView.visibility = View.INVISIBLE
                latestImageView.visibility = View.VISIBLE

                // 加载图片
                Glide.with(this)
                    .load(item.uri)
                    .into(latestImageView)
                debugLog("正在显示照片")
            }
        }
        updatePhotoCountText()

        // 让“下一张”按钮默认获得焦点，用户友好性设置
        if (focusNextButton) {
            buttonNext.post {
                buttonNext.requestFocus()
            }
        }
    }
    private fun releasePlayer() {
        // 这个方法现在只在 onDestroy 中被调用。
        // 在释放播放器本身之前，先从视图解绑。
        latestVideoView.player = null
        exoPlayer?.release()
        exoPlayer = null
    }
    private fun deleteCurrentImage() {
        if (currentImageIndex == -1 || allMediaItems.isEmpty()) {
            debugLog("删除失败：索引错误或媒体列表为空")
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
                        debugLog("成功删除媒体文件: ${mediaFile.absolutePath}")

                        // 删除成功后，通知 MediaStore 更新
                        scanFilePath(mediaFile.absolutePath)

                        // 如果是视频，尝试删除同名的 .txt 文件。
                        // 就因为rokidglasses拍摄视频时生成了这个txt，所有mediastore方式都删不掉，
                        // 才被迫换成使用MANAGE_EXTERNAL_STORAGE权限，但好在rg暂时还没有限制使用这些重要权限；-）
                        if (itemToDelete.type == MediaItem.Type.VIDEO) {
                            val txtFile = File(mediaFile.parent, "${mediaFile.nameWithoutExtension}.txt")
                            if (txtFile.exists() && txtFile.delete()) {
                                debugLog("成功删除视频附加txt文件: ${txtFile.absolutePath}")
                                // 同样通知 MediaStore 更新
                                scanFilePath(txtFile.absolutePath)
                            } else {
                                debugLog("视频附加txt文件没有找到或者删除失败: ${txtFile.absolutePath}")
                            }
                        }
                    } else {
                        debugLog("删除媒体文件失败: ${mediaFile.absolutePath}")
                    }
                } else {
                    debugLog("删除时无法从uri获取目标地址信息: ${itemToDelete.uri}")
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
                    // 阻止设置焦点到 buttonNext，用户友好性设置，方便用户连续删除操作
                    loadSpecificMedia(currentImageIndex, focusNextButton = false)
                }
                updatePhotoCountText()
                buttonDelphoto.requestFocus()
            } else {
                showCenteredToast(getString(R.string.toast_failed_to_delete_photo))
                debugLog("删除失败: ${itemToDelete.uri}")
                buttonDelphoto.requestFocus()
            }
        }
    }

    // =============================================================================================
    // UI 更新与辅助方法 (UI & Helper Methods)
    // =============================================================================================
    private fun showLoadingIndicator(show: Boolean) {
        loadingIndicator.visibility = if (show) View.VISIBLE else View.GONE
    }
    private fun handleNoPhotosFound(isError: Boolean = false) {
        val message =
            if (isError) getString(R.string.toast_error_accessing_photos) else getString(R.string.toast_no_photos_found)
        showCenteredToast(message, Toast.LENGTH_LONG)
        allMediaItems.clear()
        currentImageIndex = -1
        exoPlayer?.stop()
        latestVideoView.visibility = View.INVISIBLE
        latestImageView.visibility = View.VISIBLE

        // --- 停止任何正在进行的旧动画 ---
        // 这可以防止在函数被多次调用时动画叠加或行为异常
        flipAnimator?.cancel()
        latestImageView.scaleX = 1f // 恢复到正常状态

        val emojiBitmapToShow = createBitmapFromEmoji("🤷", 200) // 200清晰度，数值越小清晰度越低，emoji本身不大，几十几百的对性能没啥影响
        latestImageView.setImageBitmap(emojiBitmapToShow)
        latestImageView.scaleType = ImageView.ScaleType.FIT_CENTER

        val displaySizeInPixels = 140 // 显示时的大小尺寸
        val parent = latestImageView.parent as? androidx.constraintlayout.widget.ConstraintLayout
        parent?.let {
            val set = androidx.constraintlayout.widget.ConstraintSet()
            set.clone(it)

            // 在设置新约束之前，清除该视图上的所有旧约束（包括 bias）
            set.clear(latestImageView.id)

            // 直接为 ImageView 设置固定尺寸
            set.constrainWidth(latestImageView.id, displaySizeInPixels)
            set.constrainHeight(latestImageView.id, displaySizeInPixels)

            // 显示位置------
            // 将其居中
            set.connect(latestImageView.id, androidx.constraintlayout.widget.ConstraintSet.LEFT, androidx.constraintlayout.widget.ConstraintSet.PARENT_ID, androidx.constraintlayout.widget.ConstraintSet.LEFT)
            set.connect(latestImageView.id, androidx.constraintlayout.widget.ConstraintSet.RIGHT, androidx.constraintlayout.widget.ConstraintSet.PARENT_ID, androidx.constraintlayout.widget.ConstraintSet.RIGHT)
            set.connect(latestImageView.id, androidx.constraintlayout.widget.ConstraintSet.TOP, androidx.constraintlayout.widget.ConstraintSet.PARENT_ID, androidx.constraintlayout.widget.ConstraintSet.TOP)
            set.connect(latestImageView.id, androidx.constraintlayout.widget.ConstraintSet.BOTTOM, androidx.constraintlayout.widget.ConstraintSet.PARENT_ID, androidx.constraintlayout.widget.ConstraintSet.BOTTOM)

            // 设置水平和垂直偏移 (Bias)
            // 值从 0.0 (左/上) 到 1.0 (右/下)，0.5 是居中
            set.setHorizontalBias(latestImageView.id, 0.5f) // 0.5f 表示水平居中
            set.setVerticalBias(latestImageView.id, 0.55f)  // 例如，设置为 0.45f，让它在垂直方向上稍微偏上一点

            // 一次性应用所有更改
            set.applyTo(it)
        }

        // 启动循环翻转动画
        val flipInterval = 500L // 总循环周期：0.5 秒

        // 定义一个可以自我调用的函数来实现无限循环
        fun startFlipAnimation() {
            flipAnimator = latestImageView.animate()
                .scaleX(-latestImageView.scaleX) // 直接翻转到另一边
                .setDuration(0L)                 // 瞬间完成
                .setStartDelay(flipInterval)     // 每次翻转前都停顿 0.5 秒
                .withEndAction {
                    startFlipAnimation()         // 动画结束后，重新调用自己，形成循环
                }
            flipAnimator?.start()
        }

        // 首次启动动画（无延迟）
        latestImageView.animate()
            .scaleX(-1f)
            .setDuration(0L)
            .withEndAction {
                startFlipAnimation() // 完成首次翻转后，进入带延迟的循环
            }
            .start()
        updatePhotoCountText()
        buttonBackmain.visibility = View.VISIBLE
    }
    private fun updatePhotoCountText() {
        val currentNumber = if (currentImageIndex >= 0) currentImageIndex + 1 else 0
        val totalNumber = allMediaItems.size
        photoCountTextView.text = getString(R.string.photo_count_format, currentNumber, totalNumber)
        photoCountTextView.visibility = View.VISIBLE
        buttonDelphoto.visibility = if (totalNumber > 0) View.VISIBLE else View.GONE
        buttonNext.visibility = if (totalNumber > 1) View.VISIBLE else View.GONE
    }
    private fun showCenteredToast(message: String, duration: Int = Toast.LENGTH_SHORT) {
        centeredToast?.cancel()
        centeredToast = Toast.makeText(this, message, duration).apply {
            setGravity(android.view.Gravity.CENTER, 0, 120)
            show()
        }
    }
    private fun scanFilePath(path: String) {
        MediaScannerConnection.scanFile(applicationContext, arrayOf(path), null, null)
    }
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
            debugLog("从uri获取路径失败: $uri, error: ${e.message}")
        }
        // 如果查询没有返回结果或发生异常，则返回 null
        return null
    }
    private fun isUriValid(uri: Uri): Boolean {
        // MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        if (isFinishing || isDestroyed) {
            // 如果 Activity 正在销毁，则无需再进行检查
            return false
        }
        return try {
            // 尝试打开一个文件描述符。如果文件不存在或无法访问，
            // contentResolver 会抛出 FileNotFoundException。
            contentResolver.openFileDescriptor(uri, "r")?.use {
                // 如果能成功打开并自动关闭，说明文件是有效的。
                it.close()
                true
            } ?: false // 如果返回 null，也视为无效
        } catch (e: FileNotFoundException) {
            // 捕获到这个异常，明确表示文件已不存在。
            debugLog("uri检测失败: $uri. 没有找到文件")
            false
        } catch (e: SecurityException) {
            // 捕获可能的权限问题
            debugLog("uri检测失败: $uri. 权限限制")
            false
        }
    }
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
            // 此处设置保证即使用户从屏幕边缘滑入，系统栏也只是短暂显示然后自动隐藏，不会破坏应用的沉浸式布局。
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        // 让“删除”按钮默认获得焦点，用户友好性设置
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).requestFocus()
        }
        dialog.show()
    }

    // 查看窗口获得焦点时，请求next按钮获取焦点，改进用户体验
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            buttonNext.requestFocus()
        }
    }
}
