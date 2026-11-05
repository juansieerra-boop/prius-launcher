package com.juandiego.priuslauncher

import android.service.notification.NotificationListenerService

/**
 * No hace nada por sí solo. Android exige que exista para que el launcher
 * pueda ver qué app está reproduciendo música (título, artista, portada).
 */
class MediaListenerService : NotificationListenerService()
