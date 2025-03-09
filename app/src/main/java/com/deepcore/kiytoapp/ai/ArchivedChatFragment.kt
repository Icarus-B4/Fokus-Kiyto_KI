package com.deepcore.kiytoapp.ai

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.deepcore.kiytoapp.R
import com.google.android.material.appbar.MaterialToolbar
import java.text.SimpleDateFormat
import java.util.*

class ArchivedChatFragment : Fragment() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var toolbar: MaterialToolbar
    private lateinit var adapter: ChatAdapter
    private var archivedChat: ArchivedChat? = null

    companion object {
        private const val ARG_ARCHIVED_CHAT = "archived_chat"

        fun newInstance(archivedChat: ArchivedChat) = ArchivedChatFragment().apply {
            arguments = Bundle().apply {
                putString(ARG_ARCHIVED_CHAT, ChatManager.gson.toJson(archivedChat))
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.getString(ARG_ARCHIVED_CHAT)?.let { json ->
            archivedChat = ChatManager.gson.fromJson(json, ArchivedChat::class.java)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_archived_chat, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        toolbar = view.findViewById(R.id.toolbar)
        recyclerView = view.findViewById(R.id.archivedChatRecyclerView)

        setupToolbar()
        setupRecyclerView()
        loadArchivedMessages()
    }

    private fun setupToolbar() {
        archivedChat?.let { chat ->
            val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
            toolbar.title = getString(R.string.archived_chat_title, 
                dateFormat.format(Date(chat.timestamp)))
        }

        toolbar.setNavigationOnClickListener {
            parentFragmentManager.popBackStack()
        }
    }

    private fun setupRecyclerView() {
        recyclerView.layoutManager = LinearLayoutManager(context)
        adapter = ChatAdapter(
            onActionClicked = { action -> 
                // Archivierte Chats sind schreibgeschützt, daher keine Aktionen
            },
            onMessageLongClicked = { _, _ -> 
                // Archivierte Chats sind schreibgeschützt, daher keine Long-Click-Aktionen
            }
        )
        recyclerView.adapter = adapter
    }

    private fun loadArchivedMessages() {
        archivedChat?.messages?.forEach { message ->
            adapter.addMessage(message)
        }
    }
} 