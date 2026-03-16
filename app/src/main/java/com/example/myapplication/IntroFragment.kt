package com.example.myapplication

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.myapplication.databinding.FragmentIntroBinding

class IntroFragment : Fragment(R.layout.fragment_intro) {
    private var binding: FragmentIntroBinding? = null
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        binding = FragmentIntroBinding.bind(view).apply {
            lifecycleOwner = viewLifecycleOwner
            fragment = this@IntroFragment
        }
    }
    
    fun onRegisterClick() {
        findNavController().navigate(R.id.action_intro_to_register)
    }
    
    fun onLoginClick() {
        findNavController().navigate(R.id.action_intro_to_login)
    }
    
    fun onFeedClick() {
        findNavController().navigate(R.id.action_intro_to_feed)
    }
    
    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }
}
