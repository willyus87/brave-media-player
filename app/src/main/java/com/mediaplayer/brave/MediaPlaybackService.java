package com.mediaplayer.brave;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;

import androidx.core.app.NotificationCompat;
import androidx.media.session.MediaButtonReceiver;

/**
 * Servicio en primer plano que permite:
 * 1. Reproducción con pantalla bloqueada
 * 2. Reproducción con app minimizada
 * 3. Controles en la notificación y pantalla de bloqueo
 * 4. Mantener el audio activo (AudioFocus)
 */
public class MediaPlaybackService extends Service {

    private static final String CHANNEL_ID = "media_playback_channel";
    private static final String CHANNEL_NAME = "Reproducción de Medios";
    private static final int NOTIFICATION_ID = 1001;

    // Acciones para los botones de la notificación
    public static final String ACTION_PLAY_PAUSE = "com.mediaplayer.brave.PLAY_PAUSE";
    public static final String ACTION_STOP = "com.mediaplayer.brave.STOP";
    public static final String ACTION_OPEN_APP = "com.mediaplayer.brave.OPEN_APP";

    private final IBinder binder = new LocalBinder();
    private PowerManager.WakeLock wakeLock;
    private AudioManager audioManager;
    private AudioFocusRequest audioFocusRequest;
    private MediaSessionCompat mediaSession;
    private NotificationManager notificationManager;

    private boolean isPlaying = false;
    private String currentTitle = "BraveMedia Player";

    public class LocalBinder extends Binder {
        MediaPlaybackService getService() {
            return MediaPlaybackService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        initAudioFocus();
        initMediaSession();
        initWakeLock();
        notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        // Iniciar en foreground inmediatamente
        startForeground(NOTIFICATION_ID, buildNotification(currentTitle, false));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            String action = intent.getAction();
            if (action != null) {
                switch (action) {
                    case ACTION_PLAY_PAUSE:
                        sendCommandToApp(isPlaying ? "pause" : "play");
                        break;
                    case ACTION_STOP:
                        sendCommandToApp("stop");
                        stopSelf();
                        break;
                }
            }
            // Manejar botones de media (auriculares, etc.)
            MediaButtonReceiver.handleIntent(mediaSession, intent);
        }
        return START_STICKY; // Reiniciar si el sistema lo mata
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    // ─── Inicialización ──────────────────────────────────────────────────────

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW // Sin sonido para notificaciones de media
            );
            channel.setDescription("Controles de reproducción de medios");
            channel.setShowBadge(false);
            channel.setSound(null, null);

            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }

    private void initAudioFocus() {
        audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            AudioAttributes attrs = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build();

            audioFocusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(attrs)
                .setAcceptsDelayedFocusGain(true)
                .setOnAudioFocusChangeListener(focusChange -> {
                    if (focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                        // Pausa temporal (ej: llamada entrante)
                        sendCommandToApp("pause");
                    } else if (focusChange == AudioManager.AUDIOFOCUS_GAIN) {
                        // Recuperar foco de audio
                        sendCommandToApp("play");
                    } else if (focusChange == AudioManager.AUDIOFOCUS_LOSS) {
                        // Pérdida de foco permanente
                        sendCommandToApp("pause");
                    }
                })
                .build();

            audioManager.requestAudioFocus(audioFocusRequest);
        }
    }

    private void initMediaSession() {
        mediaSession = new MediaSessionCompat(this, "BraveMediaSession");
        mediaSession.setFlags(
            MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS |
            MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
        );

        // Callbacks para controles de hardware/auriculares
        mediaSession.setCallback(new MediaSessionCompat.Callback() {
            @Override
            public void onPlay() {
                sendCommandToApp("play");
                updateNotification(currentTitle, true);
            }

            @Override
            public void onPause() {
                sendCommandToApp("pause");
                updateNotification(currentTitle, false);
            }

            @Override
            public void onStop() {
                sendCommandToApp("stop");
                stopSelf();
            }

            @Override
            public void onSkipToNext() {
                sendCommandToApp("next");
            }

            @Override
            public void onSkipToPrevious() {
                sendCommandToApp("previous");
            }
        });

        mediaSession.setActive(true);
    }

    private void initWakeLock() {
        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        if (powerManager != null) {
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK, // Mantiene CPU activa, no pantalla
                "BraveMediaPlayer::WakeLock"
            );
            wakeLock.acquire(10 * 60 * 60 * 1000L); // Máximo 10 horas
        }
    }

    // ─── Notificación con controles ──────────────────────────────────────────

    public void updateNotification(String title, boolean playing) {
        this.currentTitle = title;
        this.isPlaying = playing;

        // Actualizar estado de MediaSession
        PlaybackStateCompat.Builder stateBuilder = new PlaybackStateCompat.Builder()
            .setActions(
                PlaybackStateCompat.ACTION_PLAY |
                PlaybackStateCompat.ACTION_PAUSE |
                PlaybackStateCompat.ACTION_STOP |
                PlaybackStateCompat.ACTION_PLAY_PAUSE
            );
        stateBuilder.setState(
            playing ? PlaybackStateCompat.STATE_PLAYING : PlaybackStateCompat.STATE_PAUSED,
            PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN,
            1.0f
        );
        mediaSession.setPlaybackState(stateBuilder.build());

        notificationManager.notify(NOTIFICATION_ID, buildNotification(title, playing));
    }

    private Notification buildNotification(String title, boolean playing) {
        // Intent para abrir la app
        Intent openIntent = new Intent(this, MainActivity.class);
        openIntent.setAction(ACTION_OPEN_APP);
        PendingIntent openPendingIntent = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        // Intent para Play/Pause
        Intent playPauseIntent = new Intent(this, MediaPlaybackService.class);
        playPauseIntent.setAction(ACTION_PLAY_PAUSE);
        PendingIntent playPausePendingIntent = PendingIntent.getService(
            this, 1, playPauseIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        // Intent para Detener
        Intent stopIntent = new Intent(this, MediaPlaybackService.class);
        stopIntent.setAction(ACTION_STOP);
        PendingIntent stopPendingIntent = PendingIntent.getService(
            this, 2, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        return new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(playing ? "▶ Reproduciendo" : "⏸ Pausado")
            .setContentText(title)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(openPendingIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC) // Visible en pantalla bloqueada
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true) // No se puede deslizar para cerrar
            .setShowWhen(false)
            // Botón Play/Pause
            .addAction(
                playing ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play,
                playing ? "Pausar" : "Reproducir",
                playPausePendingIntent
            )
            // Botón Detener
            .addAction(android.R.drawable.ic_delete, "Detener", stopPendingIntent)
            // Estilo de media para pantalla de bloqueo
            .setStyle(new androidx.media.app.NotificationCompat.MediaStyle()
                .setMediaSession(mediaSession.getSessionToken())
                .setShowActionsInCompactView(0, 1)) // Mostrar Play/Pause y Stop en compacto
            .build();
    }

    // ─── Comunicación con MainActivity ──────────────────────────────────────

    private void sendCommandToApp(String command) {
        // El MainActivity escucha este broadcast para ejecutar JS en el WebView
        Intent intent = new Intent("com.mediaplayer.brave.WEBVIEW_COMMAND");
        intent.putExtra("command", command);
        sendBroadcast(intent);
    }

    @Override
    public void onDestroy() {
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        if (mediaSession != null) mediaSession.release();
        if (audioFocusRequest != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioManager.abandonAudioFocusRequest(audioFocusRequest);
        }
        super.onDestroy();
    }
}
