package com.example.indoorlocation.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.indoorlocation.R;
import com.example.indoorlocation.model.Space;

import java.util.List;

public class SpaceAdapter extends RecyclerView.Adapter<SpaceAdapter.SpaceViewHolder> {
    private Context context;
    private List<Space> spaceList;
    private OnItemClickListener listener;
    private int editingPosition = -1;  // 记录当前编辑的位置，-1表示无编辑

    // 点击事件接口
    public interface OnItemClickListener {
        void onEditClick(int position);
        void onDeleteClick(int position);
        void onNameClick(int position, Space space);
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.listener = listener;
    }

    public SpaceAdapter(List<Space> spaceList) {
        this.spaceList = spaceList;
    }

    @NonNull
    @Override
    public SpaceViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.space_list, parent, false);
        return new SpaceViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull SpaceViewHolder holder, int position) {
        Space space = spaceList.get(position);
        holder.spaceName.setText(space.getSpaceName());
        holder.longitude.setText("经度：" + space.getLongitude());
        holder.latitude.setText("纬度：" + space.getLatitude());

        // 设置在线状态
        if (space.getAccess() != null && space.getAccess()) {
            holder.spaceStatus.setText("在线");
            holder.spaceStatus.setBackgroundResource(R.drawable.status_online);
            holder.spaceStatus.setTextColor(holder.itemView.getContext().getResources().getColor(R.color.onlineColor));
        } else {
            holder.spaceStatus.setText("离线");
            holder.spaceStatus.setBackgroundResource(R.drawable.status_offline);
            holder.spaceStatus.setTextColor(holder.itemView.getContext().getResources().getColor(R.color.offlineColor));
        }

        // 点击空间名称进入编辑状态
        holder.spaceName.setOnClickListener(v -> {
            if (listener != null) {
                int pos = holder.getAdapterPosition();
                if (pos != RecyclerView.NO_POSITION) {
                    listener.onNameClick(pos, space);
                }
            }
        });
    }

    @Override
    public int getItemCount() {
        return spaceList.size();
    }

    public class SpaceViewHolder extends RecyclerView.ViewHolder {
        TextView spaceName;
        TextView spaceStatus;
        TextView longitude;
        TextView latitude;
        ImageView editIcon;
        ImageView deleteIcon;

        public SpaceViewHolder(View itemView) {
            super(itemView);
            spaceName = itemView.findViewById(R.id.space_name);
            spaceStatus = itemView.findViewById(R.id.space_status);
            longitude = itemView.findViewById(R.id.longitude);
            latitude = itemView.findViewById(R.id.latitude);

            deleteIcon = itemView.findViewById(R.id.delete_icon);


            // 设置删除点击事件
            deleteIcon.setOnClickListener(v -> {
                if (listener != null) {
                    int position = getAdapterPosition();
                    if (position != RecyclerView.NO_POSITION) {
                        listener.onDeleteClick(position);
                    }
                }
            });
        }
    }


    // 更新数据列表
    public void updateData(List<Space> newSpaces) {
        spaceList = newSpaces;
        notifyDataSetChanged();
    }
}
