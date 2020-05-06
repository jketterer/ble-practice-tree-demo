package com.example.bluetoothpracticetree.utility;

import android.bluetooth.BluetoothDevice;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.bluetoothpracticetree.R;

import java.util.ArrayList;
import java.util.List;

/*
    This class creates an adapter for the RecyclerView used to display each host device in
    JoinActivity.
 */

public class HostAdapter extends RecyclerView.Adapter<HostAdapter.ViewHolder> {

    private List<BluetoothDevice> devices;
    private OnHostClickListener listener;

    public HostAdapter(OnHostClickListener listener) {
        this.devices = new ArrayList<>();
        this.listener = listener;
    }

    // TODO: Figure out how to stop multiple listings from same device, while allowing multiple devices with the same name
    public void addHostName(BluetoothDevice device) {
        if (!devices.contains(device)) {
            devices.clear();
            devices.add(device);
        }
    }

    @NonNull
    @Override
    public HostAdapter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.host_list_item, parent, false);

        return new HostAdapter.ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull HostAdapter.ViewHolder holder, final int position) {
        String name = devices.get(position).getName();
        holder.textView.setText(name != null ? name : "Unknown Host");
        holder.textView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                listener.onItemClick(devices.get(position));
            }
        });
    }

    @Override
    public int getItemCount() {
        return devices.size();
    }

    public interface OnHostClickListener {
        void onItemClick(BluetoothDevice device);
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView textView;

        ViewHolder(View v) {
            super(v);
            this.textView = v.findViewById(R.id.label);
        }
    }
}
