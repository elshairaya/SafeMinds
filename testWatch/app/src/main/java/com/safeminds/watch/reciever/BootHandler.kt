package  com.safeminds.watch.reciever
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.safeminds.watch.scheduler.Controller
import com.safeminds.watch.scheduler.HourlyWorkerScheduler

class BootHandler : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        val appContext = context ?: return
        val action = intent?.action ?: return

        if (action == Intent.ACTION_BOOT_COMPLETED
            || action == Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            HourlyWorkerScheduler.register(appContext)
            Controller.checkNightSessionNow(appContext)
        }
    }}