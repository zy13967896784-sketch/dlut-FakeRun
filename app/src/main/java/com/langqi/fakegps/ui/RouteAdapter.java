package com.langqi.fakegps.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.langqi.fakegps.R;
import com.langqi.fakegps.core.RouteStore;

import java.util.ArrayList;
import java.util.List;

/**
 * 路线库列表适配器。
 */
public class RouteAdapter extends RecyclerView.Adapter<RouteAdapter.Holder> {

    public interface Listener {
        void onRouteSelected(RouteStore.RouteSummary summary);

        /** 点击行尾「更多」按钮：重命名 / 编辑 / 导出 / 分享 / 删除。 */
        void onRouteActions(RouteStore.RouteSummary summary);
    }

    private final List<RouteStore.RouteSummary> items = new ArrayList<>();
    private final Listener listener;
    private String currentId;

    public RouteAdapter(Listener listener) {
        this.listener = listener;
    }

    public void submit(List<RouteStore.RouteSummary> summaries, String currentId) {
        items.clear();
        if (summaries != null) {
            items.addAll(summaries);
        }
        this.currentId = currentId;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_route, parent, false);
        return new Holder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        RouteStore.RouteSummary summary = items.get(position);
        boolean isCurrent = summary.id.equals(currentId);
        holder.name.setText(summary.name);
        holder.meta.setText(summary.meta() + (isCurrent ? " · 当前使用" : ""));
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onRouteSelected(summary);
            }
        });
        holder.more.setOnClickListener(v -> {
            if (listener != null) {
                listener.onRouteActions(summary);
            }
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final TextView name;
        final TextView meta;
        final ImageButton more;

        Holder(@NonNull View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.tv_item_name);
            meta = itemView.findViewById(R.id.tv_item_meta);
            more = itemView.findViewById(R.id.btn_item_more);
        }
    }
}
