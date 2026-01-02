package expo.modules.dynamicappicon

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Looper
import android.util.Log
import expo.modules.core.interfaces.ReactActivityLifecycleListener
//import android.widget.Toast
import android.os.Handler



object SharedObject {
    var packageName: String = ""
    var classesToKill = ArrayList<String>()
    var icon: String = ""
    var pm: PackageManager? = null
    var shouldChangeIcon: Boolean = false
    var isInBackground: Boolean = true
}
// For Support Contact: bashahowin@gmail.com

// Used Toast for easy Debugging purpose
class ExpoDynamicAppIconReactActivityLifecycleListener : ReactActivityLifecycleListener {

    private var currentActivity: Activity? = null
    private var isBackground = false
    private val handler = Handler(Looper.getMainLooper())
    private val backgroundCheckRunnable = Runnable {
        if (isBackground) {
            onBackground()
        }
    }

     override fun onPause(activity: Activity) {
        currentActivity = activity
        isBackground = true
        handler.postDelayed(backgroundCheckRunnable, if (SharedObject.isInBackground) 5000 else 0)
    }

    override fun onResume(activity: Activity) {
        currentActivity = activity
        isBackground = false
        handler.removeCallbacks(backgroundCheckRunnable)
        //Toast.makeText(activity, "App is in Foreground", Toast.LENGTH_SHORT).show()
    }


    override fun onDestroy(activity: Activity) {
        handler.removeCallbacks(backgroundCheckRunnable)
        //Toast.makeText(activity, "OnDestroy Triggered,shouldChangeIcon: ${SharedObject.shouldChangeIcon}", Toast.LENGTH_LONG).show()
        if(SharedObject.shouldChangeIcon){
            //Toast.makeText(activity, "OnDestroy Triggered and icon will be changed", Toast.LENGTH_LONG).show()
             applyIconChange(activity)
        }
        if (currentActivity === activity) {
            currentActivity = null
        }
       
    }
    private fun onBackground() {
        currentActivity?.let { activity ->
            //Toast.makeText(activity, "App is in Background (onStop-like)", Toast.LENGTH_SHORT).show()
            
            if (SharedObject.shouldChangeIcon) {
                applyIconChange(activity)
            }
        }
    }

private fun applyIconChange(activity: Activity) {
    SharedObject.icon.takeIf { it.isNotEmpty() }?.let { icon ->
        val pm = SharedObject.pm ?: return
        val newComponent = ComponentName(SharedObject.packageName, icon)

        if (!doesComponentExist(newComponent)) {
            SharedObject.shouldChangeIcon = false
            Log.e("IconChange", "Component does not exist: $icon")
            return
        }

        try {
            // Get all launcher activities using queryIntentActivities
            // This includes both activities and activity-aliases with launcher intent-filters
            val launcherIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
                setPackage(SharedObject.packageName)
            }
            
            val launcherActivities = pm.queryIntentActivities(
                launcherIntent,
                PackageManager.MATCH_DISABLED_COMPONENTS or PackageManager.MATCH_DEFAULT_ONLY
            )

            // Disable all launcher components except the new one
            launcherActivities.forEach { resolveInfo ->
                val componentName = resolveInfo.activityInfo.componentName
                val componentNameString = componentName.className
                
                // Skip if this is the component we want to enable
                if (componentNameString == icon) {
                    return@forEach
                }
                
                val state = pm.getComponentEnabledSetting(componentName)
                if (state != PackageManager.COMPONENT_ENABLED_STATE_DISABLED) {
                    pm.setComponentEnabledSetting(
                        componentName,
                        PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                        PackageManager.DONT_KILL_APP
                    )
                    Log.i("IconChange", "Disabled component: $componentNameString")
                }
            }

            // Enable the new icon
            pm.setComponentEnabledSetting(
                newComponent,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
            )
            Log.i("IconChange", "Enabled new icon: $icon")

        } catch (e: Exception) {
            Log.e("IconChange", "Error during icon change", e)
        } finally {
            SharedObject.shouldChangeIcon = false
        }

        // Ensure at least one component is enabled
        ensureAtLeastOneComponentEnabled(activity)
    }
}



private fun ensureAtLeastOneComponentEnabled(context: Context) {
    val pm = SharedObject.pm ?: return
    
    // Check all launcher components (activities and activity-aliases)
    val launcherIntent = Intent(Intent.ACTION_MAIN).apply {
        addCategory(Intent.CATEGORY_LAUNCHER)
        setPackage(SharedObject.packageName)
    }
    
    val launcherActivities = pm.queryIntentActivities(
        launcherIntent,
        PackageManager.MATCH_DISABLED_COMPONENTS or PackageManager.MATCH_DEFAULT_ONLY
    )

    val hasEnabledComponent = launcherActivities.any { resolveInfo ->
        val componentName = resolveInfo.activityInfo.componentName
        pm.getComponentEnabledSetting(componentName) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED
    }

    if (!hasEnabledComponent) {
        val mainActivityName = "${SharedObject.packageName}.MainActivity"
        val mainComponent = ComponentName(SharedObject.packageName, mainActivityName)
        try {
            pm.setComponentEnabledSetting(
                mainComponent,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
            )
            Log.i("IconChange", "No active component found. Re-enabling $mainActivityName")
        } catch (e: Exception) {
            Log.e("IconChange", "Error enabling fallback MainActivity", e)
        }
    }
}

    /**
     * Check if a component exists in the manifest (including disabled ones).
     * This checks both activities and activity-aliases.
     */
    private fun doesComponentExist(componentName: ComponentName): Boolean {
        return try {
            val pm = SharedObject.pm ?: return false
            
            // Try to get the component's enabled state - if it doesn't exist, this will throw or return DEFAULT
            val state = pm.getComponentEnabledSetting(componentName)
            
            // If we get here without exception, the component exists
            // We can also verify by checking if it's not in the default state (which might indicate it doesn't exist)
            // But actually, DEFAULT state is valid for components that exist but haven't been explicitly set
            // So we'll use queryIntentActivities to verify it exists
            
            val launcherIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
                setPackage(componentName.packageName)
            }
            
            val activities = pm.queryIntentActivities(
                launcherIntent,
                PackageManager.MATCH_DISABLED_COMPONENTS or PackageManager.MATCH_DEFAULT_ONLY
            )
            
            activities.any { it.activityInfo.componentName == componentName }
        } catch (e: Exception) {
            Log.e("IconChange", "Error checking if component exists: ${componentName.className}", e)
            false
        }
    }


}