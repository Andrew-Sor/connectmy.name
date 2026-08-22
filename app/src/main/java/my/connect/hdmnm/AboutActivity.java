package my.connect.hdmnm;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.DynamicColors;

public class AboutActivity extends AppCompatActivity {

    // Ссылки на репозиторий
    private static final String URL_RELEASES = "https://github.com/Andrew-Sor/connectmy.name/releases";
    private static final String URL_REPOSITORY = "https://github.com/Andrew-Sor/connectmy.name";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        DynamicColors.applyToActivityIfAvailable(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_about);

        MaterialToolbar toolbar = findViewById(R.id.toolbarAbout);
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        MaterialCardView cardReleases = findViewById(R.id.cardReleases);
        MaterialCardView cardRepository = findViewById(R.id.cardRepository);
        
        TextView tvVersion = findViewById(R.id.tvVersion);
        tvVersion.setText("Версия: " + BuildConfig.VERSION_NAME);

        cardReleases.setOnClickListener(v -> openUrl(URL_RELEASES));
        cardRepository.setOnClickListener(v -> openUrl(URL_REPOSITORY));
    }

    private void openUrl(String url) {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        startActivity(intent);
    }
}