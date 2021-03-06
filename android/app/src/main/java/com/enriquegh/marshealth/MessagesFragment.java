package com.enriquegh.marshealth;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.Toast;

import com.enriquegh.marshealth.util.BaseURLUtility;
import com.sinch.android.rtc.PushPair;
import com.sinch.android.rtc.messaging.Message;
import com.sinch.android.rtc.messaging.MessageClient;
import com.sinch.android.rtc.messaging.MessageClientListener;
import com.sinch.android.rtc.messaging.MessageDeliveryInfo;
import com.sinch.android.rtc.messaging.MessageFailureInfo;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;


public class MessagesFragment extends Fragment {

    private EditText messageBodyField;
    private String messageBody;
    private MessageService.MessageServiceInterface messageService;
    private final ServiceConnection serviceConnection = new MyServiceConnection();
    private final MessageClientListener messageClientListener = new MyMessageClientListener();
    private MessageAdapter messageAdapter;
    private EditText mTxtRecipient;
    private Spinner mLstRecipient;
    private static final String TAG = "MessagesFragment";


    /**
     * This URL needs to be configured to wherever the API and SQL are, local or remote.
     */
    static final String API_URL = BaseURLUtility.getApiURL();

    static final String STAFF_URL = "staff";

    public MessagesFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);


    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view;
        view = inflater.inflate(R.layout.activity_messaging, container, false); // inflating the layout

        Objects.requireNonNull(getActivity()).bindService(new Intent(getContext(), MessageService.class), serviceConnection, Context.BIND_AUTO_CREATE);

        mLstRecipient = view.findViewById(R.id.listRecipient);

        new RetrieveRecipients().execute();

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
        String currentUserId = prefs.getString("username", "DEFAULT_NO");

        messageBodyField = view.findViewById(R.id.txtTextBody);

        ListView messagesList = view.findViewById(R.id.lstMessages);
        messageAdapter = new MessageAdapter(getActivity());
        messagesList.setAdapter(messageAdapter);
        //listen for a click on the send button
        view.findViewById(R.id.btnSend).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String recipient = mLstRecipient.getSelectedItem().toString();
                messageBody = messageBodyField.getText().toString();
                if (messageBody.isEmpty()) {
                    Toast.makeText(getContext(), "Please enter a message", Toast.LENGTH_LONG).show();
                    return;
                }
                messageService.sendMessage(recipient, messageBody);
                Log.d(TAG, messageBody);
                messageBodyField.setText("");
            }
        });

        return view;
    }

    //unbind the service when the activity is destroyed
    @Override
    public void onDestroy() {

        super.onDestroy();
    }

    private class MyServiceConnection implements ServiceConnection {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            messageService = (MessageService.MessageServiceInterface) iBinder;
            messageService.addMessageClientListener(messageClientListener);
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            messageService = null;
            assert messageService != null;
            messageService.removeMessageClientListener(messageClientListener);
        }
    }

    private class MyMessageClientListener implements MessageClientListener {

        //Notify the user if their message failed to send
        @Override
        public void onMessageFailed(MessageClient client, Message message,
                                    MessageFailureInfo failureInfo) {
            Toast.makeText(getContext(), "Message failed to send.", Toast.LENGTH_LONG).show();
        }


        @Override
        public void onIncomingMessage(MessageClient client, Message message) {

            messageAdapter.addMessage(message, MessageAdapter.DIRECTION_INCOMING);
        }

        @Override
        public void onMessageSent(MessageClient client, Message message, String recipientId) {
            //Display the message that was just sent

            messageAdapter.addMessage(message, MessageAdapter.DIRECTION_OUTGOING);
        }

        //Do you want to notify your user when the message is delivered?
        @Override
        public void onMessageDelivered(MessageClient client, MessageDeliveryInfo deliveryInfo) {}

        @Override
        public void onShouldSendPushData(MessageClient client, Message message, List<PushPair> pushPairs) {}
    }

    @SuppressWarnings("SpellCheckingInspection")
    class RetrieveRecipients extends AsyncTask<Void, Void, String> {

        ArrayList<String> options=new ArrayList<>();


        protected void onPreExecute() {
        }

        protected String doInBackground(Void... params) {

            try {
                //For now I believe this is insecure since the filter parameter is passed
                //within the URL and could be manipulated.
                //TODO: Figure out how to send parameter through request body
                URL url = new URL(API_URL + STAFF_URL);
                HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
                try {
                    BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(urlConnection.getInputStream()));
                    StringBuilder stringBuilder = new StringBuilder();
                    String line;
                    while ((line = bufferedReader.readLine()) != null) {
                        stringBuilder.append(line).append("\n");
                    }
                    bufferedReader.close();
                    return stringBuilder.toString();
                } finally {
                    urlConnection.disconnect();
                }
            } catch (Exception e) {

                Log.e(TAG, e.toString(), e);

                return null;
            }
        }

        protected void onPostExecute(String response) {
            if (response == null) {
                response = "THERE WAS AN ERROR ON RETRIEVERECIPIENTS";
            }
            try {

                JSONObject object = (JSONObject) new JSONTokener(response).nextValue();
                JSONObject appointments = object.getJSONObject("staff");
                JSONArray recordsList = appointments.getJSONArray("records");

                for (int i = 0; i < recordsList.length(); i++) {
                    JSONArray records = recordsList.getJSONArray(i);
                    String userId = records.getString(3);

                    options.add(userId);
                    Log.d(TAG,userId);

                }
                ArrayAdapter<String> adapter;
                adapter = new ArrayAdapter<>(Objects.requireNonNull(getContext()),android.R.layout.simple_spinner_dropdown_item,options);
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                mLstRecipient.setAdapter(adapter);

            } catch (JSONException e) {
                Log.e(TAG, "JSON Error: " + e.toString(), e);
            }

        }
    }

}
