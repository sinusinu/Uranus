package kr.pe.sinu.uranus;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.SimpleItemAnimator;

import com.google.android.gms.oss.licenses.v2.OssLicensesMenuActivity;

import java.io.File;

import kr.pe.sinu.uranus.databinding.ActivitySettingsBinding;

public class SettingsActivity extends AppCompatActivity {
    ActivitySettingsBinding binding;
    SharedPreferences sp;
    SettingsAdapter adapter;
    SettingsItemClickListener itemClickListener;

    boolean cacheClearTried = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);

        binding = ActivitySettingsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        ViewCompat.setOnApplyWindowInsetsListener(binding.llSettings, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        sp = getSharedPreferences("kr.pe.sinu.uranus.prefs", MODE_PRIVATE);
        itemClickListener = new SettingsItemClickListener();

        binding.rvSettings.setLayoutManager(new LinearLayoutManager(this));
        adapter = new SettingsAdapter(this, sp, itemClickListener);
        binding.rvSettings.setAdapter(adapter);
        if (binding.rvSettings.getItemAnimator() != null) {
            ((SimpleItemAnimator)binding.rvSettings.getItemAnimator()).setSupportsChangeAnimations(false);
        }
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(R.anim.anim_fade_enter, R.anim.anim_slide_enter);
    }

    public class SettingsItemClickListener implements SettingsAdapter.OnItemClickListener {
        @Override
        public void onItemClick(String key, int position) {
            if (key == null) return;
            switch (key) {
                case "hide_album_art":
                    var newValue = sp.getInt(key, 0) == 0 ? 1 : 0;
                    sp.edit().putInt(key, newValue).apply();
                    adapter.notifyItemChanged(position);
                    break;
                case "clear_cache":
                    if (cacheClearTried) {
                        Toast.makeText(SettingsActivity.this, R.string.settings_cache_cleared, Toast.LENGTH_SHORT).show();
                        break;
                    }
                    cacheClearTried = true;
                    var mmc = MediaMetadataCache.getInstance();
                    mmc.unloadCache();
                    boolean error = false;
                    try {
                        File cacheDir = getCacheDir();
                        var cacheFiles = cacheDir.list();
                        if (cacheFiles != null) {
                            for (var child : cacheFiles) {

                                var childFile = new File(cacheDir, child);
                                if (!childFile.isDirectory()) {
                                    try {
                                        //noinspection ResultOfMethodCallIgnored
                                        childFile.delete();
                                    } catch (Exception ignored) {}
                                }
                            }
                        }
                    } catch (Exception ignored) { error = true; }
                    if (error) Toast.makeText(SettingsActivity.this, R.string.settings_error_cache_clear_failed, Toast.LENGTH_SHORT).show();
                    else Toast.makeText(SettingsActivity.this, R.string.settings_cache_cleared, Toast.LENGTH_SHORT).show();
                    break;
                case "about":
                    var intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/sinusinu/Uranus"));
                    try {
                        startActivity(intent);
                    } catch (Exception ignored) {

                    }
                    break;
                case "license":
                    startActivity(new Intent(SettingsActivity.this, OssLicensesMenuActivity.class));
                    break;
            }
        }
    }
}