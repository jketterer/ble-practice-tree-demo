package com.example.bluetoothpracticetree;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

public class SettingsActivity extends AppCompatActivity {

    EditText dialEntry;
    EditText rolloutEntry;
    EditText nameEntry;
    Button saveDial;
    Button saveRollout;
    Button saveName;
    TextView currentDial;
    TextView currentRollout;
    TextView currentName;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        dialEntry = findViewById(R.id.edit_dial);
        saveDial = findViewById(R.id.save_dial);
        rolloutEntry = findViewById(R.id.edit_rollout);
        saveRollout = findViewById(R.id.save_rollout);
        nameEntry=findViewById(R.id.edit_name);
        saveName = findViewById(R.id.save_name);
        currentDial = findViewById(R.id.current_dial);
        currentRollout = findViewById(R.id.current_rollout);
        currentName = findViewById(R.id.current_name);

        SharedPreferences sharedPref = getSharedPreferences("RACE_PREFS", Context.MODE_PRIVATE);
        final SharedPreferences.Editor editor = sharedPref.edit();

        long dial = sharedPref.getLong("dial", 10);
        setCurrentDial(dial);
        long rollout = sharedPref.getLong("rollout", 0);
        setCurrentRollout(rollout);
        String name = sharedPref.getString("name", "Default");
        setCurrentName(name);

        saveDial.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String dialString = dialEntry.getText().toString();
                Double decimal = Double.parseDouble(dialString) * 1000;
                long dial = (decimal.longValue());
                editor.putLong("dial", dial);
                editor.apply();
                setCurrentDial(dial);

                CharSequence text = "Dial-in saved!";
                Toast toast = Toast.makeText(getApplicationContext(), text, Toast.LENGTH_SHORT);
                toast.show();
            }
        });

        saveRollout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String dialString = rolloutEntry.getText().toString();
                Double decimal = Double.parseDouble(dialString);
                long rollout = (decimal.longValue());
                editor.putLong("rollout", rollout);
                editor.apply();
                setCurrentRollout(rollout);

                CharSequence text = "Rollout saved!";
                Toast toast = Toast.makeText(getApplicationContext(), text, Toast.LENGTH_SHORT);
                toast.show();
            }
        });

        saveName.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String name = nameEntry.getText().toString();
                editor.putString("name", name);
                editor.apply();
                setCurrentName(name);

                CharSequence text = "Name saved!";
                Toast toast = Toast.makeText(getApplicationContext(), text, Toast.LENGTH_SHORT);
                toast.show();
            }
        });
    }

    private void setCurrentDial(long dial) {
        String formatted = String.format("%.2f", (double) dial / 1000.0);
        currentDial.setText(getString(R.string.current, formatted));
    }

    private void setCurrentRollout(long rollout) {
        currentRollout.setText(getString(R.string.current, Long.toString(rollout)));
    }

    private void setCurrentName(String name) {
        currentName.setText(getString(R.string.current, name));
    }
}
