package org.tensorflow.lite.examples.objectdetection.fragments

import androidx.navigation.ActionOnlyNavDirections
import androidx.navigation.NavDirections
import org.tensorflow.lite.examples.objectdetection.R

public class PermissionsFragmentDirections private constructor() {
  public companion object {
    public fun actionPermissionsToCamera(): NavDirections =
        ActionOnlyNavDirections(R.id.action_permissions_to_camera)
  }
}
