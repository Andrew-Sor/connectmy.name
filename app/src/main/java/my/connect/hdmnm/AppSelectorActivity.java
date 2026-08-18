package my.connect.hdmnm;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.color.DynamicColors;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AppSelectorActivity extends AppCompatActivity {

    private RecyclerView recyclerApps;
    private ProgressBar progressApps;
    private AppAdapter adapter;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        DynamicColors.applyToActivityIfAvailable(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_app_selector);

        MaterialToolbar toolbar = findViewById(R.id.toolbarAppSelector);
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        recyclerApps = findViewById(R.id.recyclerApps);
        progressApps = findViewById(R.id.progressApps);

        recyclerApps.setLayoutManager(new LinearLayoutManager(this));
        adapter = new AppAdapter(new ArrayList<>(), this::onAppSelected);
        recyclerApps.setAdapter(adapter);

        loadApps();
    }

    private void loadApps() {
        executor.execute(() -> {
            PackageManager pm = getPackageManager();
            Intent intent = new Intent(Intent.ACTION_MAIN, null);
            intent.addCategory(Intent.CATEGORY_LAUNCHER);

            List<ResolveInfo> resolveInfos = pm.queryIntentActivities(intent, 0);
            List<AppInfo> appList = new ArrayList<>();

            for (ResolveInfo resolveInfo : resolveInfos) {
                String packageName = resolveInfo.activityInfo.packageName;
                String appName = resolveInfo.loadLabel(pm).toString();
                Drawable icon = resolveInfo.loadIcon(pm);

                appList.add(new AppInfo(appName, packageName, icon));
            }

            // Сортировка по алфавиту
            Collections.sort(appList, (a, b) -> a.name.compareToIgnoreCase(b.name));

            mainHandler.post(() -> {
                progressApps.setVisibility(View.GONE);
                adapter.setApps(appList);
            });
        });
    }

    private void onAppSelected(AppInfo app) {
        Intent resultIntent = new Intent();
        resultIntent.putExtra("app_name", app.name);
        resultIntent.putExtra("app_package", app.packageName);
        setResult(RESULT_OK, resultIntent);
        finish();
    }

    // --- Модель данных ---
    private static class AppInfo {
        String name;
        String packageName;
        Drawable icon;

        AppInfo(String name, String packageName, Drawable icon) {
            this.name = name;
            this.packageName = packageName;
            this.icon = icon;
        }
    }

    // --- Адаптер для RecyclerView ---
    private static class AppAdapter extends RecyclerView.Adapter<AppAdapter.AppViewHolder> {
        private List<AppInfo> apps;
        private final OnAppClickListener listener;

        interface OnAppClickListener {
            void onClick(AppInfo app);
        }

        AppAdapter(List<AppInfo> apps, OnAppClickListener listener) {
            this.apps = apps;
            this.listener = listener;
        }

        void setApps(List<AppInfo> apps) {
            this.apps = apps;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public AppViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_app, parent, false);
            return new AppViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull AppViewHolder holder, int position) {
            AppInfo app = apps.get(position);
            holder.tvAppName.setText(app.name);
            holder.tvAppPackage.setText(app.packageName);
            holder.ivAppIcon.setImageDrawable(app.icon);
            holder.itemView.setOnClickListener(v -> listener.onClick(app));
        }

        @Override
        public int getItemCount() {
            return apps.size();
        }

        static class AppViewHolder extends RecyclerView.ViewHolder {
            TextView tvAppName, tvAppPackage;
            ImageView ivAppIcon;

            AppViewHolder(@NonNull View itemView) {
                super(itemView);
                tvAppName = itemView.findViewById(R.id.tvAppName);
                tvAppPackage = itemView.findViewById(R.id.tvAppPackage);
                ivAppIcon = itemView.findViewById(R.id.ivAppIcon);
            }
        }
    }
}