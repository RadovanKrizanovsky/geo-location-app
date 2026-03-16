package com.example.myapplication

import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.myapplication.databinding.FragmentFeedBinding
import com.google.android.material.snackbar.Snackbar

class FeedFragment : Fragment(R.layout.fragment_feed) {
    private var binding: FragmentFeedBinding? = null
    private lateinit var viewModel: FeedViewModel

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val factory = object : ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return FeedViewModel(DataRepository.getInstance(requireContext())) as T
            }
        }
        viewModel = ViewModelProvider(this, factory)[FeedViewModel::class.java]
        
        binding = FragmentFeedBinding.bind(view).apply {
            lifecycleOwner = viewLifecycleOwner
            viewModel = this@FeedFragment.viewModel
        }.also { bnd ->
            bnd.bottomNavMenu.setActiveIcon(BottomNavigationMenu.ActiveIcon.FEED)

            bnd.feedRecyclerview.apply {
                layoutManager = LinearLayoutManager(context)
                setHasFixedSize(true)
                setItemViewCacheSize(20)
            }
            
            val feedAdapter = UserFeedAdapter(requireContext()) { user ->
                val bundle = Bundle().apply {
                    putString("userId", user.uid)
                }
                findNavController().navigate(R.id.action_feed_to_other_profile, bundle)
            }
            bnd.feedRecyclerview.adapter = feedAdapter

            viewModel.feed_items.observe(viewLifecycleOwner, Observer { items ->
                items?.let {
                    val otherUsers = it.filterNotNull().filter { user -> user.uid != "me" }
                    feedAdapter.updateItems(otherUsers)
                    Log.d("FeedFragment", "Users loaded: ${otherUsers.size} používateľov (excluding me)")
                }
            })

            viewModel.message.observe(viewLifecycleOwner, Observer { evento ->
                evento.getContentIfNotHandled()?.let { message ->
                    if (message.isNotEmpty()) {
                        Snackbar.make(view, message, Snackbar.LENGTH_LONG).show()
                    }
                }
            })

            viewModel.loading.observe(viewLifecycleOwner, Observer { isLoading ->
                Log.d("FeedFragment", "Loading state: $isLoading")
            })
        }
    }
    
    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }
}
