package kr.pe.sinu.uranus;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import kr.pe.sinu.uranus.databinding.ListSettingsItemBinding;

// TODO: something something onclick something
public class SettingsAdapter extends RecyclerView.Adapter<SettingsAdapter.ViewHolder> {
    private final Context context;
    private final SharedPreferences sp;

    private final SettingsItem[] items;

    public SettingsAdapter(Context context, SharedPreferences sp) {
        this.context = context;
        this.sp = sp;

        items = new SettingsItem[] {
                new SettingsItem(
                        context.getString(R.string.settings_category_troubleshooting),
                        null,
                        SettingsItem.TYPE_CATEGORY,
                        null
                ),
                new SettingsItem(
                        context.getString(R.string.settings_item_clear_cache_title),
                        context.getString(R.string.settings_item_clear_cache_desc),
                        SettingsItem.TYPE_BUTTON,
                        "clear_cache"
                ),
                new SettingsItem(
                        context.getString(R.string.settings_category_about),
                        null,
                        SettingsItem.TYPE_CATEGORY,
                        null
                ),
                new SettingsItem(
                        String.format(context.getString(R.string.settings_item_about_title), BuildConfig.VERSION_NAME),
                        context.getString(R.string.settings_item_about_desc),
                        SettingsItem.TYPE_BUTTON,
                        null
                ),
        };

        for (var item : items) item.fetch(sp);
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        public ListSettingsItemBinding binding;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            binding = ListSettingsItemBinding.bind(itemView);
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(LayoutInflater.from(context).inflate(R.layout.list_settings_item, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        switch (items[position].type) {
            case SettingsItem.TYPE_BUTTON:
                holder.binding.tvSettingsItemCategory.setVisibility(View.GONE);
                holder.binding.llSettingsItemItem.setVisibility(View.VISIBLE);
                holder.binding.cbSettingsItemCheck.setVisibility(View.GONE);
                holder.binding.tvSettingsItemTitle.setText(items[position].title);
                holder.binding.tvSettingsItemSubtitle.setText(items[position].desc);
                break;
            case SettingsItem.TYPE_CHECKBOX:
                holder.binding.tvSettingsItemCategory.setVisibility(View.GONE);
                holder.binding.llSettingsItemItem.setVisibility(View.VISIBLE);
                holder.binding.cbSettingsItemCheck.setVisibility(View.VISIBLE);
                holder.binding.tvSettingsItemTitle.setText(items[position].title);
                holder.binding.tvSettingsItemSubtitle.setText(items[position].desc);
                break;
            case SettingsItem.TYPE_CATEGORY:
                holder.binding.tvSettingsItemCategory.setVisibility(View.VISIBLE);
                holder.binding.tvSettingsItemCategory.setText(items[position].title);
                holder.binding.llSettingsItemItem.setVisibility(View.GONE);
                break;
        }
    }

    @Override
    public int getItemCount() {
        return items.length;
    }
}
