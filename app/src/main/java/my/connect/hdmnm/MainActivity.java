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
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Authenticator;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.PasswordAuthentication;
import java.net.Proxy;
import java.net.Socket;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
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

// Импорты jtorctl
import net.freehaven.tor.control.TorControlConnection;
import net.freehaven.tor.control.EventHandler;

public class MainActivity extends AppCompatActivity {

    private TextView tvLog;
    private ScrollView scrollLog;
    private MaterialButton btnStartStop;
    private MaterialButton btnMenu;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    
    public enum State {
        IDLE, RUNNING, TOR_START, PROCESSING, EXTRACTION, MAIL, VPN
    };
    
    public volatile boolean isRunning = false;
    public State currentState = State.IDLE;
    public int attemptCount = 1;
    
    public static String[] Emails = { "", "", "", "", "", "", "" };
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        DynamicColors.applyToActivityIfAvailable(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvLog = findViewById(R.id.tvLog);
        scrollLog = findViewById(R.id.scrollLog);
        btnStartStop = findViewById(R.id.btnStartStop);
        btnMenu = findViewById(R.id.btnMenu);

        checkFirstRun();
        loadEmails();
        
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                System.exit(0);
            };
        });
        
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
            PopupMenu popup = new PopupMenu(MainActivity.this, v);
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
    
    @Override
    protected void onResume() {
        super.onResume();
        loadEmails();
        // Вся логика переходов из onResume удалена, так как встроенный Tor 
        // работает в фоне и не требует выхода из Activity.
    }
    
    private void startProcess() {
        isRunning = true;
        attemptCount = 1;
        updateUiState(true);
        tvLog.setText("Инициализация...");
        currentState = State.TOR_START;
        startEmbeddedTor();
    };

    private void stopProcess() {
        isRunning = false;
        currentState = State.IDLE;
        updateUiState(false);
        
        // Останавливаем службу Tor принудительно
        stopService(new Intent(this, org.torproject.jni.TorService.class));
        
        if (tvLog.getText().toString().contains("Успешно!")) {
            incrementEmailIndex();
        }
        appendLog("\nОстановка...");
    };

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

    // --- НАЧАЛО БЛОКА ТOR ---

    private File buildTorrc(String bridgeLine) throws IOException {
        File torDir = getDir("tordata", MODE_PRIVATE);
        File torrcFile = new File(torDir, "torrc");
        File cookieFile = new File(torDir, "control_auth_cookie");
        
        // Создаем рабочую директорию для obfs4
        File ptDir = new File(torDir, "pt_state");
        if (!ptDir.exists()) ptDir.mkdirs();

        String nativeDir = getApplicationInfo().nativeLibraryDir;
        File obfs4Proxy = new File(nativeDir, "libobfs4proxy.so");

        StringBuilder config = new StringBuilder();
        config.append("DataDirectory ").append(torDir.getAbsolutePath()).append("\n");
        config.append("SocksPort 9050\n");
        config.append("ControlPort 9051\n");
        config.append("CookieAuthentication 1\n");
        config.append("CookieAuthFile ").append(cookieFile.getAbsolutePath()).append("\n");
        
        // Включаем подробный лог Tor в файл
        File logFile = new File(torDir, "tor_log.txt");
        config.append("Log notice file ").append(logFile.getAbsolutePath()).append("\n");

        if (bridgeLine != null && !bridgeLine.trim().isEmpty()) {
            config.append("UseBridges 1\n");
            // Прописываем путь к плагину. Если extractNativeLibs сработает, файл будет тут
            config.append("ClientTransportPlugin obfs4 exec ").append(obfs4Proxy.getAbsolutePath()).append("\n");
            config.append("Bridge ").append(bridgeLine.trim()).append("\n");
        }

        try (FileOutputStream fos = new FileOutputStream(torrcFile)) {
            fos.write(config.toString().getBytes(StandardCharsets.UTF_8));
        }
        
        // Предупреждение в наш UI лог, если библиотека не распаковалась
        if (bridgeLine != null && !bridgeLine.trim().isEmpty() && !obfs4Proxy.exists()) {
            mainHandler.post(() -> appendLog("ВНИМАНИЕ: Файл " + obfs4Proxy.getName() + " не найден в системе!"));
        }

        return torrcFile;
    }

    private void startEmbeddedTor() {
        executor.execute(() -> {
            try {
                SharedPreferences prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
                
                // Твой мост стоит по умолчанию, если в настройках ничего не введено
                String defaultBridge = "obfs4 31.171.241.238:8414 811115E721E9B530A3BA41D3FCE6B71D64E4DC5A cert=Sye+7gmUheN9ohPm8TV1pyNiuMwr4NMAKJXC3p6Du8m56VyorhG6S7u2NgklFS91rwpKBA iat-mode=0";
                String bridge = prefs.getString("tor_bridge", defaultBridge); 
                
                File torrc = buildTorrc(bridge);
                mainHandler.post(() -> appendLog("Запуск службы Tor..."));
                
                Intent torIntent = new Intent(this, org.torproject.jni.TorService.class);
                torIntent.putExtra("torrc", torrc.getAbsolutePath());
                startService(torIntent);

                // Увеличили таймаут: 120 попыток с паузой в 1 секунду = 2 минуты на подключение
                monitorTorBootstrap(120);
            } catch (Exception e) {
                mainHandler.post(() -> {
                    appendLog("Ошибка конфигурации Tor: " + e.getMessage());
                    stopProcess();
                });
            }
        });
    }

    private void monitorTorBootstrap(int retriesLeft) {
        if (!isRunning) return;
        if (retriesLeft == 0) {
            mainHandler.post(() -> {
                appendLog("Таймаут ожидания подключения к Tor.");
                stopProcess();
            });
            return;
        }

        try {
            Socket controlSocket = new Socket("127.0.0.1", 9051);
            TorControlConnection conn = new TorControlConnection(controlSocket);
            
            File torDir = getDir("tordata", MODE_PRIVATE);
            File cookieFile = new File(torDir, "control_auth_cookie");
            
            if (!cookieFile.exists()) {
                controlSocket.close();
                Thread.sleep(1000);
                monitorTorBootstrap(retriesLeft - 1);
                return;
            }
            
            byte[] cookie = Files.readAllBytes(cookieFile.toPath());
            conn.authenticate(cookie);

            conn.setEventHandler(new EventHandler() {
                @Override
                public void message(String type, String msg) {
                    if ("STATUS_CLIENT".equals(type) && msg.contains("BOOTSTRAP PROGRESS=100")) {
                        onTorReady();
                    }
                }
                
                // Исправленная сигнатура: добавились circID и path
                @Override 
                public void circuitStatus(String status, String circID, String path) {}
                
                @Override 
                public void streamStatus(String status, String streamID, String target) {}
                
                @Override 
                public void orConnStatus(String status, String orName) {}
                
                @Override 
                public void bandwidthUsed(long read, long written) {}
                
                @Override 
                public void newDescriptors(List<String> orList) {}
                
                @Override 
                public void unrecognized(String type, String msg) {}
            });
            conn.setEvents(java.util.Arrays.asList("STATUS_CLIENT"));
            
            String phase = conn.getInfo("status/bootstrap-phase");
            if (phase != null && phase.contains("PROGRESS=100")) {
                onTorReady();
            } else {
                mainHandler.post(() -> appendLog("Tor: " + (phase != null ? phase : "Подключение...")));
            }
        } catch (Exception e) {
            try { Thread.sleep(1000); } catch (InterruptedException ignored) {}
            monitorTorBootstrap(retriesLeft - 1);
        }
    }

    private void onTorReady() {
        mainHandler.post(() -> {
            if (currentState == State.TOR_START) {
                appendLog("Tor успешно подключен (100%)!");
                currentState = State.PROCESSING;
                int emailIndex = getEmailIndex();
                appendLog(String.format("Почта (%d): %s...", emailIndex, Emails[emailIndex]));
                executePostRequest();
            }
        });
    }

    // --- КОНЕЦ БЛОКА ТOR ---
    
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
    
    private void handleResponse(String html) {
        if (!isRunning) return;
        SharedPreferences prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);

        if (html.contains("Перейти в почтовый ящик")) {
            appendLog("Успешно! Отключение Tor...");
            prefs.edit().putString("success_date_time", LocalDateTime.now().toString()).apply();
            
            // Выключаем встроенный Tor перед парсингом почты
            stopService(new Intent(this, org.torproject.jni.TorService.class));
            
            startExtractionFlow();

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

    // Весь старый код из onResume переехал сюда
    private void startExtractionFlow() {
        SharedPreferences prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
        String mode = prefs.getString("extraction_mode", "IMAP");
        currentState = State.EXTRACTION;
        
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
            
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                url = "https://" + url;
            }

            currentState = State.VPN;
            appendLog("Открытие почты в браузере...");
            openBrowser(url);
        }
    }

    private void checkEmailForCode(int attempt) {
        if (!isRunning) return;

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

            List<Integer> orderToCheck = new ArrayList<>();
            if (imapCount == 7) {
                int targetIndex = getEmailIndex();
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

            for (int i = 0; i < orderToCheck.size(); i++) {
                if (!isRunning) return;

                int accountIndex = orderToCheck.get(i);
                String imapEmail = prefs.getString("imap_email_" + accountIndex, "");
                String imapPassword = prefs.getString("imap_password_" + accountIndex, "");
                String imapHost = prefs.getString("imap_host_" + accountIndex, "imap.yandex.ru");
                String imapPort = prefs.getString("imap_port_" + accountIndex, "993");
                String imapSecurity = prefs.getString("imap_security_" + accountIndex, "SSL/TLS");

                if (imapEmail.isEmpty() || imapPassword.isEmpty() || imapHost.isEmpty()) {
                    continue; 
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
                    props.put("mail." + protocol + ".connectiontimeout", "8000");
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

                if (codeFound) break; 
            }

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
        stopService(new Intent(this, org.torproject.jni.TorService.class));
        executor.shutdownNow();
        mainHandler.removeCallbacksAndMessages(null); 
    }
    
    private void checkFirstRun() {
        SharedPreferences prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
        boolean isFirstRun = prefs.getBoolean("is_first_run", true);
        if (isFirstRun) {
            new MaterialAlertDialogBuilder(this)
                .setTitle("connectmy.name")
                .setMessage("Это приложение позволяет получать неограниченное количество пробных периодов для hidemy.name VPN каждый день!\nВстроенный Tor запускается автоматически.")
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