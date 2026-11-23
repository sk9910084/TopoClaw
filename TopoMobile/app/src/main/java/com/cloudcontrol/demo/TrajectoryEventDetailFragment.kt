package com.cloudcontrol.demo

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.cloudcontrol.demo.databinding.FragmentTrajectoryEventDetailBinding
import com.google.gson.Gson
import kotlinx.coroutines.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * 轨迹事件详情Fragment
 * 显示指定会话中的所有事件详情
 */
class TrajectoryEventDetailFragment : Fragment() {
    
    companion object {
        private const val TAG = "TrajectoryEventDetail"
        private const val ARG_FILE_NAME = "file_name"
        private val TIME_FORMAT = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
        
        fun newInstance(fileName: String): TrajectoryEventDetailFragment {
            return TrajectoryEventDetailFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_FILE_NAME, fileName)
                }
            }
        }
    }
    
    private var _binding: FragmentTrajectoryEventDetailBinding? = null
    private val binding get() = _binding!!
    private val mainScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    private lateinit var adapter: TrajectoryEventAdapter
    private val events = mutableListOf<TrajectoryEvent>()
    private var session: TrajectorySession? = null
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTrajectoryEventDetailBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // 立即隐藏ActionBar（事件详情页面有自己的标题栏）
        (activity as? MainActivity)?.let { mainActivity ->
            if (mainActivity.supportActionBar?.isShowing == true) {
                mainActivity.hideActionBarInstantly()
            }
        }
        
        // 设置标题栏背景颜色为浅灰色
        binding.llTitleBar.setBackgroundColor(0xFFF5F5F5.toInt())
        
        // 返回按钮
        binding.btnBack.setOnClickListener {
            parentFragmentManager.popBackStack()
        }
        
        // 设置RecyclerView
        adapter = TrajectoryEventAdapter(events)
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter
        
        // 加载事件详情
        loadEventDetails()
    }
    
    override fun onResume() {
        super.onResume()
        // 检查Fragment是否已附加到Activity
        if (!isAdded || context == null) return
        
        // 确保ActionBar隐藏（事件详情页面有自己的标题栏）
        (activity as? MainActivity)?.let { mainActivity ->
            if (mainActivity.supportActionBar?.isShowing == true) {
                mainActivity.hideActionBarInstantly()
            }
            // 设置状态栏颜色为浅灰色，与顶部导航栏保持一致
            mainActivity.setStatusBarColor(0xFFF5F5F5.toInt(), lightStatusBar = true)
            // 事件详情页面不显示底部导航栏
            mainActivity.setBottomNavigationVisibility(false)
        }
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        mainScope.cancel()
        _binding = null
    }
    
    /**
     * 加载事件详情
     */
    private fun loadEventDetails() {
        val fileName = arguments?.getString(ARG_FILE_NAME) ?: return
        
        mainScope.launch {
            try {
                val sessionData = withContext(Dispatchers.IO) {
                    loadSessionFromFile(fileName)
                }
                
                if (sessionData != null) {
                    session = sessionData
                    events.clear()
                    events.addAll(sessionData.events)
                    
                    // 按时间戳排序，确保事件按实际发生时间顺序显示
                    // 使用稳定排序，相同时间戳的事件保持原有顺序
                    // 先按时间戳排序，如果时间戳相同，则按事件类型排序以确保稳定性
                    events.sortWith(compareBy<TrajectoryEvent> { it.timestamp }
                        .thenBy { it.type.name })
                    
                    Log.d(TAG, "已加载 ${events.size} 个事件，已按时间戳排序")
                    if (events.isNotEmpty()) {
                        Log.d(TAG, "第一个事件: ${events.first().type} @ ${events.first().timestamp}")
                        Log.d(TAG, "最后一个事件: ${events.last().type} @ ${events.last().timestamp}")
                    }
                    
                    adapter.notifyDataSetChanged()
                    
                    // 更新标题和统计信息
                    binding.tvTitle.text = "Event details (${events.size})"
                    updateStatistics()
                    
                    // 隐藏空状态
                    binding.tvEmptyState.visibility = View.GONE
                    binding.recyclerView.visibility = View.VISIBLE
                } else {
                    binding.tvEmptyState.visibility = View.VISIBLE
                    binding.recyclerView.visibility = View.GONE
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "加载事件详情失败: ${e.message}", e)
                binding.tvEmptyState.visibility = View.VISIBLE
                binding.recyclerView.visibility = View.GONE
            }
        }
    }
    
    /**
     * 从文件加载会话数据
     */
    private fun loadSessionFromFile(fileName: String): TrajectorySession? {
        return try {
            val recordsDir = File(requireContext().getExternalFilesDir(null), "trajectory_records")
            val file = File(recordsDir, fileName)
            
            if (!file.exists()) {
                Log.e(TAG, "文件不存在: $fileName")
                return null
            }
            
            val json = file.readText()
            val gson = Gson()
            gson.fromJson(json, TrajectorySession::class.java)
        } catch (e: Exception) {
            Log.e(TAG, "加载会话文件失败: $fileName, ${e.message}", e)
            null
        }
    }
    
    /**
     * 更新统计信息
     */
    private fun updateStatistics() {
        val clickCount = events.count { it.type == TrajectoryEventType.CLICK }
        val swipeCount = events.count { it.type == TrajectoryEventType.SWIPE }
        val textCount = events.count { it.type == TrajectoryEventType.TEXT_CHANGE || it.type == TrajectoryEventType.TEXT_INPUT }
        val windowCount = events.count { it.type == TrajectoryEventType.WINDOW_CHANGE }
        val clipboardCount = events.count { it.type == TrajectoryEventType.CLIPBOARD_CHANGE }
        
        val stats = "Taps: $clickCount | Swipes: $swipeCount | Inputs: $textCount | Windows: $windowCount | Clipboard: $clipboardCount"
        binding.tvStatistics.text = stats
    }
    
    /**
     * RecyclerView适配器
     */
    class TrajectoryEventAdapter(
        private val events: List<TrajectoryEvent>
    ) : RecyclerView.Adapter<TrajectoryEventAdapter.ViewHolder>() {
        
        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvEventType: TextView = view.findViewById(R.id.tvEventType)
            val tvTime: TextView = view.findViewById(R.id.tvTime)
            val tvDetails: TextView = view.findViewById(R.id.tvDetails)
        }
        
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_trajectory_event, parent, false)
            return ViewHolder(view)
        }
        
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val event = events[position]
            
            // 事件类型
            val typeText = when (event.type) {
                TrajectoryEventType.CLICK -> "Tap"
                TrajectoryEventType.LONG_CLICK -> "Long press"
                TrajectoryEventType.SWIPE -> "Swipe"
                TrajectoryEventType.SCROLL -> "Scroll"
                TrajectoryEventType.TEXT_INPUT -> "Text input"
                TrajectoryEventType.TEXT_CHANGE -> "Text changed"
                TrajectoryEventType.WINDOW_CHANGE -> "Window changed"
                TrajectoryEventType.TOUCH -> "Touch"
                TrajectoryEventType.KEYBOARD_SHOW -> "Keyboard shown"
                TrajectoryEventType.KEYBOARD_HIDE -> "Keyboard hidden"
                TrajectoryEventType.CLIPBOARD_CHANGE -> "Clipboard changed"
                TrajectoryEventType.BACK_BUTTON -> "back"
                TrajectoryEventType.HOME_BUTTON -> "home"
                TrajectoryEventType.SESSION_END -> "Session ended"
            }
            holder.tvEventType.text = typeText
            
            // 时间
            val relativeTime = TIME_FORMAT.format(Date(event.timestamp))
            holder.tvTime.text = relativeTime
            
            // 详细信息
            val details = buildString {
                when (event.type) {
                    TrajectoryEventType.CLICK, TrajectoryEventType.LONG_CLICK -> {
                        if (event.x != null && event.y != null) {
                            append("Coordinates: (${event.x}, ${event.y})")
                        }
                        if (event.text != null) {
                            append("\nText: ${event.text}")
                        }
                        if (event.contentDescription != null) {
                            append("\nDescription: ${event.contentDescription}")
                        }
                        if (event.packageName != null) {
                            append("\nApp: ${event.packageName}")
                        }
                    }
                    TrajectoryEventType.SWIPE -> {
                        if (event.startX != null && event.startY != null && 
                            event.endX != null && event.endY != null) {
                            append("From (${event.startX}, ${event.startY}) to (${event.endX}, ${event.endY})")
                        }
                        if (event.packageName != null) {
                            append("\nApp: ${event.packageName}")
                        }
                    }
                    TrajectoryEventType.SCROLL -> {
                        if (event.scrollX != null && event.scrollY != null) {
                            append("Scroll offset: (${event.scrollX}, ${event.scrollY})")
                        }
                        if (event.packageName != null) {
                            append("\nApp: ${event.packageName}")
                        }
                    }
                    TrajectoryEventType.TEXT_INPUT, TrajectoryEventType.TEXT_CHANGE -> {
                        if (event.text != null) {
                            append("Text: ${event.text}")
                        }
                        if (event.x != null && event.y != null) {
                            append("\nCoordinates: (${event.x}, ${event.y})")
                        }
                        if (event.packageName != null) {
                            append("\nApp: ${event.packageName}")
                        }
                    }
                    TrajectoryEventType.WINDOW_CHANGE -> {
                        if (event.packageName != null) {
                            append("App: ${event.packageName}")
                        }
                        if (event.className != null) {
                            append("\nClass: ${event.className}")
                        }
                        if (event.text != null) {
                            append("\nDescription: ${event.text}")
                        }
                    }
                    TrajectoryEventType.TOUCH -> {
                        if (event.x != null && event.y != null) {
                            append("Coordinates: (${event.x}, ${event.y})")
                        }
                        if (event.action != null) {
                            val actionText = when (event.action) {
                                0 -> "ACTION_DOWN"
                                1 -> "ACTION_UP"
                                2 -> "ACTION_MOVE"
                                else -> "ACTION_${event.action}"
                            }
                            append("\nAction: $actionText")
                        }
                    }
                    TrajectoryEventType.KEYBOARD_SHOW, TrajectoryEventType.KEYBOARD_HIDE -> {
                        append("Keyboard state changed")
                        if (event.packageName != null) {
                            append("\nApp: ${event.packageName}")
                        }
                    }
                    TrajectoryEventType.CLIPBOARD_CHANGE -> {
                        append("Clipboard content changed")
                        if (!event.text.isNullOrEmpty()) {
                            append("\nContent: ${event.text}")
                        }
                        if (event.packageName != null) {
                            append("\nApp: ${event.packageName}")
                        }
                        if (event.className != null) {
                            append("\nClass: ${event.className}")
                        }
                    }
                    TrajectoryEventType.BACK_BUTTON -> {
                        append("Back button tapped")
                        if (event.packageName != null) {
                            append("\nApp: ${event.packageName}")
                        }
                    }
                    TrajectoryEventType.HOME_BUTTON -> {
                        append("Home button tapped")
                        if (event.packageName != null) {
                            append("\nApp: ${event.packageName}")
                        }
                    }
                    TrajectoryEventType.SESSION_END -> {
                        append("Last screenshot when recording stopped")
                    }
                }
            }
            
            holder.tvDetails.text = details.ifEmpty { "No details" }
        }
        
        override fun getItemCount() = events.size
    }
}

