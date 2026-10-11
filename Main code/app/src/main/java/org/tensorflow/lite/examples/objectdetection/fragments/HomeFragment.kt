package org.tensorflow.lite.examples.objectdetection.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import org.tensorflow.lite.examples.objectdetection.CocoHelper
import org.tensorflow.lite.examples.objectdetection.MainActivity
import org.tensorflow.lite.examples.objectdetection.R
import org.tensorflow.lite.examples.objectdetection.databinding.FragmentHomeBinding
import java.lang.ref.WeakReference

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.cameraIcon.setOnClickListener {
            findNavController().navigate(R.id.action_homeFragment_to_cameraFragment)
        }

        binding.scanImagesButton.setOnClickListener {
            val activityRef = WeakReference(requireActivity() as MainActivity)

            CocoHelper(
                context = requireContext(),
                onStateChanged = { state ->
                    activityRef.get()?.showExperimentState(state)
                }
            ).scanImages()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}