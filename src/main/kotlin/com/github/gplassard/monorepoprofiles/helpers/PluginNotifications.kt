package com.github.gplassard.monorepoprofiles.helpers

import com.github.gplassard.monorepoprofiles.MyBundle
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications

class PluginNotifications {
    companion object {

        fun info(title: String, message: String) {
            Notifications.Bus.notify(
                Notification(
                    MyBundle.message("notifications.group"),
                    title,
                    message,
                    NotificationType.INFORMATION,
                )
            )
        }
    }
}
