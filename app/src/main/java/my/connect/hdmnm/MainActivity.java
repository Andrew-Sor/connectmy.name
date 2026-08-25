package my.connect.hdmnm;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.color.DynamicColors;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Authenticator;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.PasswordAuthentication;
import java.net.Proxy;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.ArrayList;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import androidx.appcompat.widget.PopupMenu; 

// Импорты JavaMail API
import javax.mail.Flags;
import javax.mail.Folder;
import javax.mail.Message;
import javax.mail.Session;
import javax.mail.Store;
import javax.mail.search.AndTerm;
import javax.mail.search.FlagTerm;
import javax.mail.search.FromStringTerm;
import javax.mail.search.SearchTerm;

import IPtProxy.IPtProxy;
import IPtProxy.Controller;

public class MainActivity extends AppCompatActivity {

    private TextView tvLog;
    private ScrollView scrollLog;
    private MaterialButton btnStartStop;
    private MaterialButton btnMenu;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    
    public enum State {
        IDLE, RUNNING, ORBOT_START, PROCESSING, ORBOT_STOP, MAIL, VPN
    };
    
    // Выполнение, состояние и попытка
    public volatile boolean isRunning = false;
    public State currentState = State.IDLE;
    public int attemptCount = 1;
    
    // Адреса почт
    public static String[] Emails = {
            "",
            "",
            "",
            "",
            "",
            "",
            ""
    };
    
    // При создании
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        DynamicColors.applyToActivityIfAvailable(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvLog = findViewById(R.id.tvLog);
        scrollLog = findViewById(R.id.scrollLog);
        btnStartStop = findViewById(R.id.btnStartStop);
        btnMenu = findViewById(R.id.btnMenu);

        checkFirstRun(); // Приветствие
        loadEmails(); // Загрузка адресов почт из настроек
        
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                System.exit(0);
            };
        });
        
        // Слушатели
        tvLog.setOnClickListener(v -> {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("HDMNM Log", tvLog.getText().toString());
            if (clipboard != null) {
                clipboard.setPrimaryClip(clip);
                showSystemToast("Лог скопирован.");
            }
        });

        btnStartStop.setOnClickListener(v -> {
            if (isRunning || currentState != State.IDLE) {
                stopProcess();
            } else {
                startProcess();
            }
        });
        
        btnMenu.setOnClickListener(v -> {
            androidx.appcompat.widget.PopupMenu popup = new androidx.appcompat.widget.PopupMenu(MainActivity.this, v);
            popup.getMenuInflater().inflate(R.menu.main_menu, popup.getMenu());
            popup.setOnMenuItemClickListener(item -> {
                if (item.getItemId() == R.id.action_settings) {
                    startActivity(new Intent(MainActivity.this, SettingsActivity.class));
                    return true;
                } else if (item.getItemId() == R.id.action_help) {
                    showSystemToast("Раздел 'Справка' в разработке");
                    return true;
                } else if (item.getItemId() == R.id.action_about) {
                    startActivity(new Intent(MainActivity.this, AboutActivity.class));
                    return true;
                }
                return false;
            });
            popup.show();
        });
        
        auloLaunchHDMN();
    };
    
    @Override
    protected void onNewIntent(Intent arg0) {
        super.onNewIntent(arg0);
        
        auloLaunchHDMN();
    };
    
    // Запуск
    private void startProcess() {
        isRunning = true;
        attemptCount = 1;
        updateUiState(true);
        tvLog.setText("Запуск...");
        SharedPreferences prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
        boolean bypassOrbot = prefs.getBoolean("bypass_orbot", false);
                if(!bypassOrbot) {
                    if (isAppInstalled("org.torproject.android")) {
                        appendLog("Запуск Orbot...");
                        showSystemToast("Подключитесь к Tor.");
                        currentState = State.ORBOT_START;
                        openApp("org.torproject.android");
                    } else {
                        appendLog("Orbot не установлен!");
                        showSystemToast("Установите Orbot и вернитесь.");
                        currentState = State.RUNNING;
                        openPlayStore("org.torproject.android");
                    };
                } else {
                    currentState = State.ORBOT_START;
                    onResume();
                }
    };

    // Остановка
    private void stopProcess() {
        isRunning = false;
        currentState = State.IDLE;
        updateUiState(false);
        if (tvLog.getText().toString().contains("Успешно!")) {
            incrementEmailIndex();
        };
        appendLog("\nОстановка...");
    };

    // Изменение кнопки старта
    private void updateUiState(boolean active) {
        btnStartStop.setText(active ? "Стоп" : "Получить код");

        if (active) {
            int colorError = MaterialColors.getColor(btnStartStop, androidx.appcompat.R.attr.colorError);
            int colorOnError = MaterialColors.getColor(btnStartStop, com.google.android.material.R.attr.colorOnError);
            btnStartStop.setBackgroundTintList(ColorStateList.valueOf(colorError));
            btnStartStop.setTextColor(colorOnError);
        } else {
            int colorPrimary = MaterialColors.getColor(btnStartStop, androidx.appcompat.R.attr.colorPrimary);
            int colorOnPrimary = MaterialColors.getColor(btnStartStop, com.google.android.material.R.attr.colorOnPrimary);
            btnStartStop.setBackgroundTintList(ColorStateList.valueOf(colorPrimary));
            btnStartStop.setTextColor(colorOnPrimary);
        };
    };

    // При возвращении
    @Override
    protected void onResume() {
        super.onResume();
        loadEmails(); // Загрузка адресов почт из настроек
        SharedPreferences prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
        
        if (!isRunning) return; 

        switch (currentState) {
            case RUNNING:
                boolean bypassOrbot = prefs.getBoolean("bypass_orbot", false);
                if(!bypassOrbot) {
                    if (isAppInstalled("org.torproject.android")) {
                        appendLog("Запуск Orbot...");
                        showSystemToast("Подключитесь к Tor.");
                        currentState = State.ORBOT_START;
                        openApp("org.torproject.android");
                    } else {
                        appendLog("Orbot не установлен!");
                        showSystemToast("Установите Orbot и вернитесь.");
                        openPlayStore("org.torproject.android");
                    };
                } else {
                    currentState = State.ORBOT_START;
                    onResume();
                }
                break;
            case ORBOT_START:
                currentState = State.PROCESSING;
                int emailIndex = getEmailIndex();
                appendLog(String.format("Почта (%d): %s...", emailIndex, Emails[emailIndex]));
                executePostRequest();
                break;

            case ORBOT_STOP:
                String mode = prefs.getString("extraction_mode", "IMAP");
                
                if ("IMAP".equals(mode)) {
                    currentState = State.MAIL;
                    appendLog("Подключение к почте (IMAP)...");
                    showSystemToast("Поиск кода...");
                    checkEmailForCode(1);
                } else if ("Приложение".equals(mode)) {
                    int appIndex = prefs.getInt("app_preset_index", 0);
                    boolean openPlayStore = prefs.getBoolean("app_open_playstore", true);
                    
                    if (appIndex >= SettingsActivity.APP_PRESETS.length) appIndex = 0; 

                    String targetName = SettingsActivity.APP_PRESETS[appIndex];
                    String targetPackage;

                    // Если выбрано "Свой", берем пакет из сохраненного кастома
                    if ("Свой".equals(targetName)) {
                        targetPackage = prefs.getString("custom_app_package", "");
                        targetName = prefs.getString("custom_app_name", "Своё приложение");
                    } else {
                        targetPackage = SettingsActivity.APP_PACKAGES[appIndex];
                    }

                    if (targetPackage.isEmpty()) {
                        appendLog("Пользовательское приложение не выбрано!");
                        showSystemToast("Выберите приложение в настройках.");
                        stopProcess();
                        return;
                    }

                    if (isAppInstalled(targetPackage)) {
                        appendLog("Запуск " + targetName + "...");
                        showSystemToast("Скопируйте код и вернитесь.");
                        currentState = State.VPN;
                        openApp(targetPackage);
                    } else {
                        appendLog(targetName + " не установлена!");
                        if (openPlayStore) {
                            showSystemToast("Установите приложение и вернитесь.");
                            openPlayStore(targetPackage);
                        } else {
                            showSystemToast(targetName + " не найдена.");
                            stopProcess();
                        }
                    }
                } else if ("Браузер".equals(mode)) {
                    String url = prefs.getString("browser_url", "https://mail.yandex.ru");
                    
                    if (url.isEmpty()) {
                        appendLog("Ошибка: Адрес почты не указан!");
                        showSystemToast("Укажите Web-адрес в настройках.");
                        stopProcess();
                        return;
                    }
                    
                    // Если пользователь в режиме "Своя" ввел адрес без http/https, добавляем (иначе браузер не откроется)
                    if (!url.startsWith("http://") && !url.startsWith("https://")) {
                        url = "https://" + url;
                    }

                    currentState = State.VPN;
                    appendLog("Открытие почты в браузере...");
                    openBrowser(url);
                }
                break;

            case MAIL:
                // Ничего не делаем
                break;

            case VPN:
                if (isAppInstalled("com.fourksoft.openvpn")) {
                    appendLog("Запуск HDMNM VPN...");
                    openApp("com.fourksoft.openvpn");
                    showSystemToast("Выберите \"Активировать\" и \"Уже есть аккаунт. Войти\". Код подставится автоматически.");
                    appendLog("Конец лога.");
                    stopProcess();
                    if(prefs.getBoolean("exit_on_end", true)) {
                        System.exit(0);
                    }
                            
                } else {
                    appendLog("HDMNM VPN не установлен!");
                    openPlayStore("com.fourksoft.openvpn");
                    showSystemToast("Установите Hidemy.name VPN и вернитесь.");
                }
                break;
                
            case PROCESSING:
            case IDLE:
                break;
        };
    };
    
    // Запрос кода со сменой IP
    private void executePostRequest() {
        if (!isRunning) return;

        executor.execute(() -> {
            HttpURLConnection conn = null;
            try {
                final String randomUser = UUID.randomUUID().toString().substring(0, 8);
                final String randomPassword = UUID.randomUUID().toString().substring(0, 8);

                Authenticator.setDefault(new Authenticator() {
                    @Override
                    protected PasswordAuthentication getPasswordAuthentication() {
                        return new PasswordAuthentication(randomUser, randomPassword.toCharArray());
                    }
                });

                Proxy socksProxy = new Proxy(Proxy.Type.SOCKS, new InetSocketAddress("127.0.0.1", 9050));
                URL url = new URL("https://hide-my-name.me/demo/success/");
                
                conn = (HttpURLConnection) url.openConnection(socksProxy);
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(15000);

                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36");
                conn.setRequestProperty("Connection", "close"); 

                String emailToSubmit = Emails[getEmailIndex()];
                String postData = "demo_mail=" + URLEncoder.encode(emailToSubmit, "UTF-8");
                byte[] postDataBytes = postData.getBytes(StandardCharsets.UTF_8);

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(postDataBytes);
                }

                int responseCode = conn.getResponseCode();
                java.io.InputStream inputStream = (responseCode >= 200 && responseCode < 400) 
                        ? conn.getInputStream() 
                        : conn.getErrorStream();

                StringBuilder responseBuilder = new StringBuilder();
                if (inputStream != null) {
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            responseBuilder.append(line);
                        }
                    }
                }

                String responseText = responseBuilder.toString();
                mainHandler.post(() -> handleResponse(responseText));

            } catch (Exception e) {
                if (!isRunning) return;
                mainHandler.post(() -> {
                    attemptCount++;
                    appendLog(String.format("Ошибка сети! Попытка %d, почта %s...", attemptCount, Emails[getEmailIndex()]));
                    mainHandler.postDelayed(this::executePostRequest, 3000);
                });
            } finally {
                if (conn != null) conn.disconnect();
            }
        });
    };
    
    // Проверка ответа
    private void handleResponse(String html) {
        if (!isRunning) return;
        SharedPreferences prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);

        if (html.contains("Перейти в почтовый ящик")) {
            appendLog("Успешно! Запуск Orbot...");
            boolean bypassOrbot = prefs.getBoolean("bypass_orbot", false);
            prefs.edit().putString("success_date_time", LocalDateTime.now().toString()).apply();
            
                if(!bypassOrbot) {
                    showSystemToast("Отключите Tor.");
                    currentState = State.ORBOT_STOP;
                    openApp("org.torproject.android");
                } else {
                    currentState = State.ORBOT_STOP;
                    onResume();
                }

        } else if (html.contains("Тестовый доступ")) {
            attemptCount++;
            appendLog(String.format("Плохой ответ! Попытка %d, почта %s...", attemptCount, Emails[getEmailIndex()]));
            mainHandler.postDelayed(this::executePostRequest, 1000);

        } else {
            attemptCount++;
            appendLog(String.format("Неизвестный ответ! Попытка %d, почта %s...", attemptCount, Emails[getEmailIndex()]));
            mainHandler.postDelayed(this::executePostRequest, 2000);
        };
    };

    // IMAP
    private void checkEmailForCode(int attempt) {
        if (!isRunning) return;

        // Ограничение количества попыток
        if (attempt > 5) {
            mainHandler.post(() -> {
                appendLog("Лимит попыток!");
                stopProcess();
            });
            return;
        }

        executor.execute(() -> {
            SharedPreferences prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
            int imapCount = prefs.getInt("imap_count", 0);
            
            if (imapCount == 0) {
                mainHandler.post(() -> {
                    appendLog("Ошибка: Не добавлены аккаунты IMAP в настройках!");
                    stopProcess();
                });
                return;
            }

            // Формируем порядок проверки аккаунтов
            List<Integer> orderToCheck = new ArrayList<>();
            if (imapCount == 7) {
                int targetIndex = getEmailIndex(); // Индекс текущей запрашиваемой почты
                orderToCheck.add(targetIndex);
                for (int i = 0; i < 7; i++) {
                    if (i != targetIndex) {
                        orderToCheck.add(i);
                    }
                }
            } else {
                for (int i = 0; i < imapCount; i++) {
                    orderToCheck.add(i);
                }
            }

            boolean codeFound = false;

            // Итеративно проходим по сформированному списку почт
            for (int i = 0; i < orderToCheck.size(); i++) {
                if (!isRunning) return;

                int accountIndex = orderToCheck.get(i);
                String imapEmail = prefs.getString("imap_email_" + accountIndex, "");
                String imapPassword = prefs.getString("imap_password_" + accountIndex, "");
                String imapHost = prefs.getString("imap_host_" + accountIndex, "imap.yandex.ru");
                String imapPort = prefs.getString("imap_port_" + accountIndex, "993");
                String imapSecurity = prefs.getString("imap_security_" + accountIndex, "SSL/TLS");

                if (imapEmail.isEmpty() || imapPassword.isEmpty() || imapHost.isEmpty()) {
                    continue; // Пропускаем недозаполненные настройки
                }

                mainHandler.post(() -> appendLog("Проверка: " + imapEmail + "..."));

                Store store = null;
                Folder inbox = null;
                try {
                    String protocol = "SSL/TLS".equals(imapSecurity) ? "imaps" : "imap";

                    Properties props = new Properties();
                    props.put("mail.store.protocol", protocol);
                    props.put("mail." + protocol + ".host", imapHost);
                    props.put("mail." + protocol + ".port", imapPort);
                    props.put("mail." + protocol + ".connectiontimeout", "8000"); // Слегка уменьшил таймаут для ускорения перебора
                    props.put("mail." + protocol + ".timeout", "8000");

                    if ("SSL/TLS".equals(imapSecurity)) {
                        props.put("mail.imaps.ssl.enable", "true");
                    } else if ("STARTTLS".equals(imapSecurity)) {
                        props.put("mail.imap.starttls.enable", "true");
                    }

                    Session session = Session.getInstance(props, null);
                    store = session.getStore(protocol);
                    store.connect(imapHost, imapEmail, imapPassword);

                    inbox = store.getFolder("INBOX");
                    inbox.open(Folder.READ_WRITE);

                    SearchTerm unreadTerm = new FlagTerm(new Flags(Flags.Flag.SEEN), false);
                    SearchTerm senderTerm = new FromStringTerm("feedback@hidemy.name");
                    SearchTerm finalSearchTerm = new AndTerm(unreadTerm, senderTerm);

                    Message[] messages = inbox.search(finalSearchTerm);

                    if (messages != null && messages.length > 0) {
                        Message latestMsg = messages[messages.length - 1];
                        String subject = latestMsg.getSubject();
                        latestMsg.setFlag(Flags.Flag.SEEN, true);

                        final String vpnCode = subject.replace("Ваш код:", "").trim();
                        codeFound = true;
                        
                        mainHandler.post(() -> {
                            appendLog("Код найден на почте " + imapEmail + ": " + vpnCode);
                            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                            ClipData clip = ClipData.newPlainText("VPN Code", vpnCode);
                            if (clipboard != null) clipboard.setPrimaryClip(clip);
                            
                            if (isAppInstalled("com.fourksoft.openvpn")) {
                                appendLog("Запуск HDMNM VPN...");
                                openApp("com.fourksoft.openvpn");
                                showSystemToast("Выберите \"Активировать\" и \"Уже есть аккаунт. Войти\".");
                                appendLog("Конец лога.");
                                stopProcess();
                                if(prefs.getBoolean("exit_on_end", true)) {
                                System.exit(0);
                                }
                                
                            } else {
                                appendLog("HDMNM VPN не установлен!");
                                openPlayStore("com.fourksoft.openvpn");
                                showSystemToast("Установите Hidemy.name VPN и вернитесь.");
                            }
                        });
                    }
                } catch (Exception e) {
                    final String err = e.getMessage();
                    mainHandler.post(() -> appendLog("Ошибка (" + imapEmail + "): " + err));
                } finally {
                    try {
                        if (inbox != null && inbox.isOpen()) inbox.close(false);
                        if (store != null && store.isConnected()) store.close();
                    } catch (Exception ignored) {}
                }

                // Если код найден, выходим из цикла перебора почт
                if (codeFound) break; 
            }

            // Если прошли все почты и ничего не нашли, ждем 3 сек и начинаем новую попытку поиска
            if (!codeFound && isRunning) {
                mainHandler.post(() -> {
                    appendLog("Попытка поиска " + attempt + " завершена. Ожидание...");
                    mainHandler.postDelayed(() -> checkEmailForCode(attempt + 1), 3000);
                });
            }
        });
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdownNow(); // Принудительно завершаем фоновые задачи
        mainHandler.removeCallbacksAndMessages(null); // Очищаем очередь хэндлера
    }
    
    // Остальные методы

    private void checkFirstRun() {
        SharedPreferences prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
        boolean isFirstRun = prefs.getBoolean("is_first_run", true);
        if (isFirstRun) {
            new MaterialAlertDialogBuilder(this)
                .setTitle("connectmy.name")
                .setMessage("Это приложение позволяет получать неограниченное количество пробных периодов для hidemy.name VPN каждый день!\nРекомендуется заранее установить Orbot и hidemy.name VPN.")
                .setPositiveButton("Ок", (dialog, which) -> {
                    prefs.edit().putBoolean("is_first_run", false).apply();
                })
                .setCancelable(false)
                .show();
        };
    };
    
    private void auloLaunchHDMN() {
        SharedPreferences prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
        String successDateTime = prefs.getString("success_date_time", null);
        
        if(prefs.getBoolean("launcher_mode", false) && successDateTime != null) {
            LocalDateTime succesDay = LocalDateTime.parse(successDateTime);
            if(Duration.between(succesDay, LocalDateTime.now()).toMinutes() <= 24*60) {
                openApp("com.fourksoft.openvpn");
            }
        }
    };
    
    private void loadEmails() {
        for (int i = 0; i < 7; i++) {
            SharedPreferences prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
            Emails[i] = prefs.getString("email_" + i, Emails[i]);
        };
    };

    public int getEmailIndex() {
        SharedPreferences prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
        int index = prefs.getInt("email_index", 0);
        return (index >= Emails.length) ? 0 : index;
    };

    public void incrementEmailIndex() {
        SharedPreferences prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
        int index = (getEmailIndex() + 1) % Emails.length;
        prefs.edit().putInt("email_index", index).apply();
    };
    
    private void appendLog(String message) {
        mainHandler.post(() -> {
            tvLog.append(message + "\n");
            scrollLog.post(() -> scrollLog.fullScroll(ScrollView.FOCUS_DOWN));
        });
    };

    private void showSystemToast(String text) {
        Toast.makeText(MainActivity.this, text, Toast.LENGTH_LONG).show();
    };
    
    private boolean isAppInstalled(String packageName) {
        try {
            getPackageManager().getPackageInfo(packageName, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    };

    private void openApp(String packageName) {
        Intent launchIntent = getPackageManager().getLaunchIntentForPackage(packageName);
        if (launchIntent != null) {
            startActivity(launchIntent);
        };
    };

    private void openPlayStore(String packageName) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + packageName)));
        } catch (android.content.ActivityNotFoundException anfe) {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=" + packageName)));
        };
    };

    private void openBrowser(String url) {
        Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        browserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(browserIntent);
        } catch (Exception e) {
            showSystemToast("Не удалось открыть браузер!");
        };
    };
}