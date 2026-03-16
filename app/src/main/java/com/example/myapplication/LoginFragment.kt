package com.example.myapplication

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import com.example.myapplication.databinding.FragmentLoginBinding
import com.google.android.material.snackbar.Snackbar

class LoginFragment : Fragment(R.layout.fragment_login) {
    private var binding: FragmentLoginBinding? = null
    private lateinit var viewModel: AuthViewModel
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        viewModel = ViewModelProvider(requireActivity(), object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return AuthViewModel(DataRepository.getInstance(requireContext())) as T
            }
        })[AuthViewModel::class.java]
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        binding = FragmentLoginBinding.bind(view).apply {
            lifecycleOwner = viewLifecycleOwner
            fragment = this@LoginFragment
            model = viewModel
        }
        
        viewModel.loginResult.observe(viewLifecycleOwner) { result ->
            if (result == null) return@observe
            
            if (result.second != null) {
                PreferenceData.getInstance().putUser(requireContext(), result.second)
                
                Snackbar.make(
                    requireView(),
                    getString(R.string.snackbar_login_success),
                    Snackbar.LENGTH_SHORT
                ).show()
                
                findNavController().navigate(R.id.action_login_to_feed)
            } else {
                Snackbar.make(
                    requireView(),
                    result.first,
                    Snackbar.LENGTH_LONG
                ).show()
            }
        }
    }
    
    fun onLoginClick() {
        viewModel.loginUser()
    }
    
    fun onForgotPasswordClick() {
        findNavController().navigate(R.id.action_login_to_forgot_password)
    }
    
    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }
}
