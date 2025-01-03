package org.tensorflow.lite.examples.objectdetection.fragments

import androidx.navigation.ActionOnlyNavDirections
import androidx.navigation.NavDirections
import org.tensorflow.lite.examples.objectdetection.R

public class HomeFragmentDirections private constructor() {
  public companion object {
    public fun actionHomeFragmentToCameraFragment(): NavDirections =
        ActionOnlyNavDirections(R.id.action_homeFragment_to_cameraFragment)
  }
}
