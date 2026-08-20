package my.connect.hdmnm;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.color.DynamicColors;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import java.util.ArrayList;
import java.util.List;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

public class SettingsActivity extends AppCompatActivity {

    // Список адресов почт
    private TextInputEditText[] emailInputs = new TextInputEditText[7];
    
    // Выбор режима
    private AutoCompleteTextView spinnerExtractionMode;
    private final String[] extractionModes = {"IMAP", "Приложение", "Браузер"};
    private TextView tvDynamicSettingsTitle; // Заголовок настроек выбранного режима
    
    // Настройки режима IMAP
    private LinearLayout layoutImapSettings, layoutAppSettings;
    private LinearLayout containerImapAccounts;
    private MaterialButton btnAddImapAccount;
    private final List<ImapAccountViewHolder> imapAccountViews = new ArrayList<>();
    private final String[] imapPresets = {"Яндекс Почта", "Gmail", "Mail.ru", "Свой"};
    private final String[] imapSecurities = {"SSL/TLS", "STARTTLS", "Нет"};
    
    // Настройки режима Приложения
    private AutoCompleteTextView spinnerAppPreset;
    private TextView tvSelectedCustomApp;
    private com.google.android.material.checkbox.MaterialCheckBox cbOpenPlayStore;
    public static final String[] APP_PRESETS = {"Яндекс Почта", "Gmail", "Mail.ru", "Свой"};
    public static final String[] APP_PACKAGES = {"ru.yandex.mail", "com.google.android.gm", "ru.mail.mailapp", ""};
    private String customAppName = "";
    private String customAppPackage = "";
    private ActivityResultLauncher<Intent> appSelectorLauncher;
    
    // Настройки режима Браузера
    private LinearLayout layoutBrowserSettings;
    private AutoCompleteTextView spinnerBrowserPreset;
    public static final String[] BROWSER_PRESETS = {"Яндекс Почта", "Gmail", "Mail.ru", "Своя"};
    private TextInputLayout tilBrowserUrl;
    private TextInputEditText etBrowserUrl;
    
    // Дополнительные настройки
    private MaterialSwitch swLauncherMode;
    
    // Настройки для разработчиков
    private TextView tvCurrentIndex;
    private MaterialButton btnAddMailIndex;
    private com.google.android.material.checkbox.MaterialCheckBox cbBypassOrbot;
    
    // Настройки бэкапа
    private MaterialButton btnImportSettings;
    private MaterialButton btnExportSettings;
    private ActivityResultLauncher<String> exportLauncher;
    private ActivityResultLauncher<String[]> importLauncher;

    // При создании
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        DynamicColors.applyToActivityIfAvailable(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        MaterialToolbar toolbar = findViewById(R.id.toolbarSettings);
        setSupportActionBar(toolbar);
        
        // Перехват выхода для проверки заполнения настроек IMAP
        toolbar.setNavigationOnClickListener(v -> validateAndExit());
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                validateAndExit();
            }
        });

        emailInputs[0] = findViewById(R.id.etEmail1);
        emailInputs[1] = findViewById(R.id.etEmail2);
        emailInputs[2] = findViewById(R.id.etEmail3);
        emailInputs[3] = findViewById(R.id.etEmail4);
        emailInputs[4] = findViewById(R.id.etEmail5);
        emailInputs[5] = findViewById(R.id.etEmail6);
        emailInputs[6] = findViewById(R.id.etEmail7);

        spinnerExtractionMode = findViewById(R.id.spinnerExtractionMode);
        layoutImapSettings = findViewById(R.id.layoutImapSettings);
        layoutAppSettings = findViewById(R.id.layoutAppSettings);
        tvDynamicSettingsTitle = findViewById(R.id.tvDynamicSettingsTitle);
        containerImapAccounts = findViewById(R.id.containerImapAccounts);
        btnAddImapAccount = findViewById(R.id.btnAddImapAccount);
        spinnerAppPreset = findViewById(R.id.spinnerAppPreset);
        cbOpenPlayStore = findViewById(R.id.cbOpenPlayStore);
        layoutBrowserSettings = findViewById(R.id.layoutBrowserSettings);
        spinnerBrowserPreset = findViewById(R.id.spinnerBrowserPreset);
        tilBrowserUrl = findViewById(R.id.tilBrowserUrl);
        etBrowserUrl = findViewById(R.id.etBrowserUrl);
        tvCurrentIndex = findViewById(R.id.tvCurrentIndex);
        btnAddMailIndex = findViewById(R.id.btnAddMailIndex);
        cbBypassOrbot = findViewById(R.id.cbBypassOrbot);
        tvSelectedCustomApp = findViewById(R.id.tvSelectedCustomApp);
        btnImportSettings = findViewById(R.id.btnImportSettings);
        btnExportSettings = findViewById(R.id.btnExportSettings);
        swLauncherMode = findViewById(R.id.swLauncherMode);

        // Лончер для сохранения файла
        exportLauncher = registerForActivityResult(new ActivityResultContracts.CreateDocument("application/octet-stream"), uri -> {
            if (uri != null) exportSettingsToFile(uri);
        });

        // Лончер для открытия файла
        importLauncher = registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
            if (uri != null) importSettingsFromFile(uri);
        });

        btnExportSettings.setOnClickListener(v -> {
            saveSettings(); // Сохраняем текущие введенные данные перед экспортом
            exportLauncher.launch("cmn.bak");
        });

        btnImportSettings.setOnClickListener(v -> importLauncher.launch(new String[]{"*/*"}));

        // Обработчик для списка приложений
        spinnerAppPreset.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, APP_PRESETS));
        spinnerAppPreset.setOnItemClickListener((parent, view, position, id) -> {
            if ("Свой".equals(APP_PRESETS[position])) {
                appSelectorLauncher.launch(new Intent(SettingsActivity.this, AppSelectorActivity.class));
            } else {
                tvSelectedCustomApp.setVisibility(View.GONE);
            }
        });

        // Обработчик для метода извлечения кода
        spinnerExtractionMode.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, extractionModes));
        spinnerExtractionMode.setOnItemClickListener((parent, view, position, id) -> {
            updateDynamicCardContent(extractionModes[position]);
        });

        btnAddImapAccount.setOnClickListener(v -> {
            if (imapAccountViews.size() < 7) {
                addImapAccountView("", "", "Яндекс Почта", "imap.yandex.ru", "993", "SSL/TLS");
            }
        });
        
        btnAddMailIndex.setOnClickListener(v -> {
            SharedPreferences prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
            int curI = prefs.getInt("email_index", 0);
            int index = (curI + 1) % MainActivity.Emails.length;
            prefs.edit().putInt("email_index", index).apply();
            updateTvCurrentIndex();
        });

        // Регистрация контракта для получения ответа из AppSelectorActivity
        appSelectorLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        customAppName = result.getData().getStringExtra("app_name");
                        customAppPackage = result.getData().getStringExtra("app_package");
                        updateCustomAppText();
                    } else {
                        // Если пользователь нажал "Назад" ничего не выбрав, и у нас до этого ничего не было
                        updateCustomAppText();
                    }
                }
        );

        spinnerBrowserPreset.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, BROWSER_PRESETS));
        spinnerBrowserPreset.setOnItemClickListener((parent, view, position, id) -> {
            applyBrowserPreset(BROWSER_PRESETS[position]);
        });
        
        loadDataToUI();
    }

    private void updateDynamicCardContent(String mode) {
        if ("IMAP".equals(mode)) {
            tvDynamicSettingsTitle.setText("Настройки IMAP");
            layoutImapSettings.setVisibility(View.VISIBLE);
            layoutAppSettings.setVisibility(View.GONE);
            layoutBrowserSettings.setVisibility(View.GONE); // Добавлено
        } else if ("Приложение".equals(mode)) {
            tvDynamicSettingsTitle.setText("Настройки приложения");
            layoutImapSettings.setVisibility(View.GONE);
            layoutAppSettings.setVisibility(View.VISIBLE);
            layoutBrowserSettings.setVisibility(View.GONE); // Добавлено
        } else if ("Браузер".equals(mode)) {
            tvDynamicSettingsTitle.setText("Настройки браузера");
            layoutImapSettings.setVisibility(View.GONE);
            layoutAppSettings.setVisibility(View.GONE);
            layoutBrowserSettings.setVisibility(View.VISIBLE);
        };
    };

    private void addImapAccountView(String email, String password, String preset, String host, String port, String security) {
        View view = LayoutInflater.from(this).inflate(R.layout.item_imap_account, containerImapAccounts, false);
        ImapAccountViewHolder holder = new ImapAccountViewHolder(view);

        holder.spinnerPreset.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, imapPresets));
        holder.spinnerSecurity.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, imapSecurities));

        holder.spinnerPreset.setOnItemClickListener((parent, v, position, id) -> applyImapPreset(holder, imapPresets[position]));

        holder.etEmail.setText(email);
        holder.etPassword.setText(password);
        holder.spinnerPreset.setText(preset, false);
        holder.etHost.setText(host);
        holder.etPort.setText(port);
        holder.spinnerSecurity.setText(security, false);

        applyImapPreset(holder, preset);

        holder.btnRemove.setOnClickListener(v -> {
            containerImapAccounts.removeView(view);
            imapAccountViews.remove(holder);
            updateImapUiState();
        });

        containerImapAccounts.addView(view);
        imapAccountViews.add(holder);
        updateImapUiState();
    }

    private void applyImapPreset(ImapAccountViewHolder holder, String preset) {
        boolean isCustom = "Свой".equals(preset);
        holder.tilHost.setEnabled(isCustom);
        holder.tilPort.setEnabled(isCustom);
        holder.tilSecurity.setEnabled(isCustom);

        if (!isCustom) {
            holder.spinnerSecurity.setText("SSL/TLS", false);
            if ("Яндекс Почта".equals(preset)) {
                holder.etHost.setText("imap.yandex.ru");
                holder.etPort.setText("993");
            } else if ("Gmail".equals(preset)) {
                holder.etHost.setText("imap.gmail.com");
                holder.etPort.setText("993");
            } else if ("Mail.ru".equals(preset)) {
                holder.etHost.setText("imap.mail.ru");
                holder.etPort.setText("993");
            }
        }
    }

    private void updateImapUiState() {
        int size = imapAccountViews.size();
        btnAddImapAccount.setVisibility(size >= 7 ? View.GONE : View.VISIBLE);

        for (int i = 0; i < size; i++) {
            ImapAccountViewHolder holder = imapAccountViews.get(i);
            holder.tvTitle.setText("Аккаунт #" + (i + 1));
            holder.btnRemove.setVisibility(size >= 2 ? View.VISIBLE : View.GONE);
        }
    }

    private void loadDataToUI() {
        SharedPreferences prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
        
        String savedMode = prefs.getString("extraction_mode", "IMAP");
        spinnerExtractionMode.setText(savedMode, false);
        updateDynamicCardContent(savedMode);

        for (int i = 0; i < 7; i++) {
            emailInputs[i].setText(prefs.getString("email_" + i, MainActivity.Emails[i]));
        }
        
        // Загрузка настроек режима "Приложение"
        int savedAppIndex = prefs.getInt("app_preset_index", 0);
        if (savedAppIndex >= APP_PRESETS.length) savedAppIndex = 0; // Защита от выхода за пределы массива при изменении длины
        spinnerAppPreset.setText(APP_PRESETS[savedAppIndex], false);
        customAppName = prefs.getString("custom_app_name", "");
        customAppPackage = prefs.getString("custom_app_package", "");
        updateCustomAppText();
        cbOpenPlayStore.setChecked(prefs.getBoolean("app_open_playstore", true));
        
        // Загрузка настроек режима "Браузер"
        String savedBrowserPreset = prefs.getString("browser_preset", "Яндекс Почта");
        spinnerBrowserPreset.setText(savedBrowserPreset, false);
        etBrowserUrl.setText(prefs.getString("browser_url", "https://mail.yandex.ru"));
        applyBrowserPreset(savedBrowserPreset); // Применяем состояние поля (активно/неактивно)
        
        cbBypassOrbot.setChecked(prefs.getBoolean("bypass_orbot", false));

        // Загрузка динамических IMAP аккаунтов
        int imapCount = prefs.getInt("imap_count", 0);
        if (imapCount == 0) {
            // Миграция старых настроек (если приложение обновляется с 1 аккаунта)
            String oldEmail = prefs.getString("imap_email", "");
            if (!oldEmail.isEmpty()) {
                addImapAccountView(oldEmail, prefs.getString("imap_password", ""), prefs.getString("imap_preset", "Яндекс Почта"),
                        prefs.getString("imap_host", "imap.yandex.ru"), prefs.getString("imap_port", "993"), prefs.getString("imap_security", "SSL/TLS"));
            } else {
                addImapAccountView("", "", "Яндекс Почта", "imap.yandex.ru", "993", "SSL/TLS"); // Дефолтный 1 аккаунт
            }
        } else {
            for (int i = 0; i < imapCount; i++) {
                addImapAccountView(
                        prefs.getString("imap_email_" + i, ""),
                        prefs.getString("imap_password_" + i, ""),
                        prefs.getString("imap_preset_" + i, "Яндекс Почта"),
                        prefs.getString("imap_host_" + i, "imap.yandex.ru"),
                        prefs.getString("imap_port_" + i, "993"),
                        prefs.getString("imap_security_" + i, "SSL/TLS")
                );
            }
        }
        updateTvCurrentIndex();
        swLauncherMode.setChecked(prefs.getBoolean("launcher_mode", false));
    };

    @Override
    protected void onPause() {
        super.onPause();
        saveSettings();
    };
    
    private void saveSettings() {
        SharedPreferences prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        
        editor.putString("extraction_mode", spinnerExtractionMode.getText().toString());
        
        // Сохранение настроек режима "Приложение"
        int selectedAppIndex = 0;
        String currentAppText = spinnerAppPreset.getText().toString();
        for (int i = 0; i < APP_PRESETS.length; i++) {
            if (APP_PRESETS[i].equals(currentAppText)) {
                selectedAppIndex = i;
                break;
            }
        }
        editor.putInt("app_preset_index", selectedAppIndex);
        editor.putBoolean("app_open_playstore", cbOpenPlayStore.isChecked());
        
        for (int i = 0; i < 7; i++) {
            String text = emailInputs[i].getText() != null ? emailInputs[i].getText().toString().trim() : "";
            editor.putString("email_" + i, text.isEmpty() ? MainActivity.Emails[i] : text);
        };
        
        editor.putString("custom_app_name", customAppName);
        editor.putString("custom_app_package", customAppPackage);
        
        // Сохранение настроек режима "Браузер"
        editor.putString("browser_preset", spinnerBrowserPreset.getText().toString());
        editor.putString("browser_url", etBrowserUrl.getText() != null ? etBrowserUrl.getText().toString().trim() : "");

        editor.putBoolean("bypass_orbot", cbBypassOrbot.isChecked());
        editor.putBoolean("launcher_mode", swLauncherMode.isChecked());

        // Сохранение IMAP аккаунтов
        editor.putInt("imap_count", imapAccountViews.size());
        for (int i = 0; i < imapAccountViews.size(); i++) {
            ImapAccountViewHolder holder = imapAccountViews.get(i);
            editor.putString("imap_email_" + i, holder.etEmail.getText().toString().trim());
            editor.putString("imap_password_" + i, holder.etPassword.getText().toString().trim());
            editor.putString("imap_preset_" + i, holder.spinnerPreset.getText().toString());
            editor.putString("imap_host_" + i, holder.etHost.getText().toString().trim());
            editor.putString("imap_port_" + i, holder.etPort.getText().toString().trim());
            editor.putString("imap_security_" + i, holder.spinnerSecurity.getText().toString());
        }

        editor.apply();
    };
    
    private void validateAndExit() {
        boolean hasError = false;

        // Проходимся по списку с конца (в обратном порядке!), 
        // чтобы безопасно удалять элементы, не ломая индексы цикла.
        for (int i = imapAccountViews.size() - 1; i >= 0; i--) {
            ImapAccountViewHolder holder = imapAccountViews.get(i);
            String email = holder.etEmail.getText() != null ? holder.etEmail.getText().toString().trim() : "";

            if (email.isEmpty()) {
                if (imapAccountViews.size() > 1) {
                    // Если это не последняя почта в списке — просто удаляем её
                    containerImapAccounts.removeView(holder.rootView);
                    imapAccountViews.remove(i);
                } else {
                    // Если осталась всего одна почта, и она пустая — подсвечиваем ошибку
                    holder.tilEmail.setErrorEnabled(true);
                    holder.tilEmail.setError("Укажите хотя бы один адрес");
                    hasError = true;
                }
            } else {
                // Если адрес заполнен, на всякий случай очищаем ошибку
                holder.tilEmail.setErrorEnabled(false);
                holder.tilEmail.setError(null);
            }
        }

        // Обновляем нумерацию (Аккаунт #1, #2 и т.д.) после возможных удалений
        updateImapUiState();

        // Если ошибок нет (т.е. осталась хотя бы одна заполненная почта) — выходим.
        // Вызов finish() автоматически запустит onPause(), который сохранит всё как надо.
        if (!hasError || !"IMAP".equals(spinnerExtractionMode.getText().toString())) {
            finish(); 
        };
        
    };
    
    private void updateCustomAppText() {
        if ("Свой".equals(spinnerAppPreset.getText().toString())) {
            tvSelectedCustomApp.setVisibility(View.VISIBLE);
            if (customAppPackage.isEmpty()) {
                tvSelectedCustomApp.setText("Приложение не выбрано (нажмите на список, чтобы выбрать)");
            } else {
                tvSelectedCustomApp.setText("Выбрано: " + customAppName + "\n(" + customAppPackage + ")");
            }
        } else {
            tvSelectedCustomApp.setVisibility(View.GONE);
        }
    };
    
    private void applyBrowserPreset(String preset) {
        boolean isCustom = "Своя".equals(preset);
        tilBrowserUrl.setEnabled(isCustom); // Включаем/отключаем поле ввода
        
        if (!isCustom) {
            if ("Яндекс Почта".equals(preset)) {
                etBrowserUrl.setText("https://mail.yandex.ru");
            } else if ("Gmail".equals(preset)) {
                etBrowserUrl.setText("https://mail.google.com");
            } else if ("Mail.ru".equals(preset)) {
                etBrowserUrl.setText("https://e.mail.ru");
            }
        }
    };
    
    private void updateTvCurrentIndex() {
        SharedPreferences prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
        int curIndex = prefs.getInt("email_index", 0);
        tvCurrentIndex.setText(String.format("Тек. индекс почты: %d", curIndex));
    };
    
    private void exportSettingsToFile(android.net.Uri uri) {
        try {
            SharedPreferences prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
            java.util.Map<String, ?> allEntries = prefs.getAll();
            org.json.JSONObject jsonObject = new org.json.JSONObject();

            for (java.util.Map.Entry<String, ?> entry : allEntries.entrySet()) {
                jsonObject.put(entry.getKey(), entry.getValue());
            }

            java.io.OutputStream outputStream = getContentResolver().openOutputStream(uri);
            if (outputStream != null) {
                outputStream.write(jsonObject.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                outputStream.close();
                Toast.makeText(this, "Настройки успешно экспортированы", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Ошибка при экспорте: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void importSettingsFromFile(android.net.Uri uri) {
        try {
            java.io.InputStream inputStream = getContentResolver().openInputStream(uri);
            if (inputStream != null) {
                java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(inputStream));
                StringBuilder stringBuilder = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    stringBuilder.append(line);
                }
                inputStream.close();

                org.json.JSONObject jsonObject = new org.json.JSONObject(stringBuilder.toString());
                SharedPreferences prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
                SharedPreferences.Editor editor = prefs.edit();

                // Очищаем текущие настройки перед записью новых
                editor.clear();

                java.util.Iterator<String> keys = jsonObject.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    Object value = jsonObject.get(key);

                    if (value instanceof String) editor.putString(key, (String) value);
                    else if (value instanceof Integer) editor.putInt(key, (Integer) value);
                    else if (value instanceof Boolean) editor.putBoolean(key, (Boolean) value);
                    else if (value instanceof Float) editor.putFloat(key, (Float) value);
                    else if (value instanceof Long) editor.putLong(key, (Long) value);
                }
                editor.apply();

                // Очищаем контейнер IMAP, чтобы loadDataToUI() не продублировал карточки
                containerImapAccounts.removeAllViews();
                imapAccountViews.clear();

                // Применяем импортированные настройки к UI
                loadDataToUI();

                Toast.makeText(this, "Настройки успешно импортированы", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Ошибка импорта: проверьте файл .bak", Toast.LENGTH_LONG).show();
        }
    };

    // Внутренний класс для хранения ссылок на View одного аккаунта IMAP
    private static class ImapAccountViewHolder {
        View rootView;
        TextView tvTitle;
        TextInputEditText etEmail, etPassword, etHost, etPort;
        TextInputLayout tilEmail, tilHost, tilPort, tilSecurity; // Добавили tilEmail
        AutoCompleteTextView spinnerPreset, spinnerSecurity;
        MaterialButton btnRemove;

        ImapAccountViewHolder(View v) {
            rootView = v; // Сохраняем корневой View
            tvTitle = v.findViewById(R.id.tvAccountTitle);
            
            tilEmail = v.findViewById(R.id.tilImapEmail); // Инициализируем наш новый ID
            etEmail = v.findViewById(R.id.etImapEmail);
            etPassword = v.findViewById(R.id.etImapPassword);
            etHost = v.findViewById(R.id.etImapHost);
            etPort = v.findViewById(R.id.etImapPort);
            
            tilHost = v.findViewById(R.id.tilImapHost);
            tilPort = v.findViewById(R.id.tilImapPort);
            tilSecurity = v.findViewById(R.id.tilImapSecurity);
            spinnerPreset = v.findViewById(R.id.spinnerImapPreset);
            spinnerSecurity = v.findViewById(R.id.spinnerImapSecurity);
            btnRemove = v.findViewById(R.id.btnRemoveAccount);
        }
    };
}