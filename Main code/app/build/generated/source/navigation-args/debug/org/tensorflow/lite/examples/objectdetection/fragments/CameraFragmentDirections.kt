package org.tensorflow.lite.examples.objectdetection.fragments

import androidx.navigation.ActionOnlyNavDirections
import androidx.navigation.NavDirections
import org.tensorflow.lite.examples.objectdetection.R

public class CameraFragmentDirections private constructor() {
  public companion object {
    public fun actionCameraToPermissions(): NavDirections =
        ActionOnlyNavDirections(R.id.action_camera_to_permissions)
  }
}
