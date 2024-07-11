package com.example.instabank;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.DatePicker;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class StatementOfAccountActivity extends AppCompatActivity {

    private Button btnStartDatePicker;
    private Button btnEndDatePicker;
    private Button btnPrint;
    private ListView listViewTransactions;
    private DatabaseReference transactionsRef;
    private FirebaseUser currentUser;
    private List<Transaction> transactionList;
    private TransactionAdapter adapter;
    private Calendar startDate;
    private Calendar endDate;
    private WebView webView;

    private TextView textViewTotalDeducted;
    private TextView textViewTotalAdded;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_statement_of_account);

        // Initialize Firebase
        currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser != null) {
            transactionsRef = FirebaseDatabase.getInstance().getReference()
                    .child("users")
                    .child(currentUser.getUid())
                    .child("transactions");
        }

        // Initialize UI elements
        btnStartDatePicker = findViewById(R.id.btnStartDatePicker);
        btnEndDatePicker = findViewById(R.id.btnEndDatePicker);
        btnPrint = findViewById(R.id.btnPrint);
        listViewTransactions = findViewById(R.id.listViewTransactions);
        webView = new WebView(this);

        // Initialize total deducted and total added TextViews
        textViewTotalDeducted = findViewById(R.id.textViewTotalDeducted);
        textViewTotalAdded = findViewById(R.id.textViewTotalAdded);

        // Initialize transaction list and adapter
        transactionList = new ArrayList<>();
        adapter = new TransactionAdapter(transactionList);
        listViewTransactions.setAdapter(adapter);

        // Set up date picker buttons
        btnStartDatePicker.setOnClickListener(v -> showDatePickerDialog(true));
        btnEndDatePicker.setOnClickListener(v -> showDatePickerDialog(false));

        // Set print button click listener
        btnPrint.setOnClickListener(v -> printTransactions());

        // Set item click listener
        listViewTransactions.setOnItemClickListener((parent, view, position, id) -> {
            Transaction transaction = transactionList.get(position);
            Intent intent = new Intent(StatementOfAccountActivity.this, ReceiptActivity.class);
            intent.putExtra("name", transaction.getName());
            intent.putExtra("accountNumber", transaction.getAccountNumber());
            intent.putExtra("amount", transaction.getAmount());
            intent.putExtra("source", transaction.getSource());
            intent.putExtra("timestamp", transaction.getTimestamp());
            intent.putExtra("referenceId", transaction.getReferenceId());
            startActivity(intent);
        });

        // Load transactions initially for the default date range or as needed
        loadTransactionsInRange(startDate, endDate);
    }


    private void showDatePickerDialog(final boolean isStartDate) {
        Calendar initialCalendar = Calendar.getInstance();
        DatePickerDialog datePickerDialog = new DatePickerDialog(
                StatementOfAccountActivity.this,
                (view, year, month, dayOfMonth) -> {
                    Calendar selectedCalendar = Calendar.getInstance();
                    selectedCalendar.set(Calendar.YEAR, year);
                    selectedCalendar.set(Calendar.MONTH, month);
                    selectedCalendar.set(Calendar.DAY_OF_MONTH, dayOfMonth);

                    if (isStartDate) {
                        startDate = selectedCalendar;
                        btnStartDatePicker.setText(getFormattedDateString(startDate));
                        loadTransactionsInRange(startDate, endDate);
                    } else {
                        endDate = selectedCalendar;
                        btnEndDatePicker.setText(getFormattedDateString(endDate));
                        loadTransactionsInRange(startDate, endDate);
                    }
                },
                initialCalendar.get(Calendar.YEAR),
                initialCalendar.get(Calendar.MONTH),
                initialCalendar.get(Calendar.DAY_OF_MONTH)
        );

        // Show date picker dialog
        datePickerDialog.show();
    }

    private void loadTransactionsInRange(Calendar start, Calendar end) {
        if (transactionsRef != null && start != null && end != null) {
            transactionsRef.addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                    transactionList.clear();
                    SimpleDateFormat dateFormat = new SimpleDateFormat("MM/dd/yyyy", Locale.getDefault());

                    double totalAdded = 0.0;
                    double totalDeducted = 0.0;

                    for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                        String name = snapshot.child("name").getValue(String.class);
                        String accountNumber = snapshot.child("accountNumber").getValue(String.class);
                        Double amount = snapshot.child("amount").getValue(Double.class);
                        String source = snapshot.child("source").getValue(String.class);
                        Long timestamp = snapshot.child("timestamp").getValue(Long.class);
                        String referenceId = snapshot.child("referenceId").getValue(String.class);

                        if (timestamp != null) {
                            Date transactionDate = new Date(timestamp);
                            Calendar transactionCalendar = Calendar.getInstance();
                            transactionCalendar.setTime(transactionDate);

                            if (transactionCalendar.compareTo(start) >= 0 && transactionCalendar.compareTo(end) <= 0) {
                                if (accountNumber != null && amount != null && source != null && referenceId != null) {
                                    Transaction transaction = new Transaction(name, accountNumber, amount, source, timestamp, referenceId);
                                    transactionList.add(transaction);

                                    // Calculate total added and deducted
                                    if (amount > 0) {
                                        totalAdded += amount;
                                    } else {
                                        totalDeducted += amount;
                                    }
                                }
                            }
                        }
                    }

                    // Sort transactions by timestamp (most recent first)
                    transactionList.sort((t1, t2) -> Long.compare(t2.getTimestamp(), t1.getTimestamp()));

                    // Update adapter data
                    adapter.notifyDataSetChanged();

                    // Display total added and deducted
                    textViewTotalAdded.setText("Total Added: " + String.format(Locale.getDefault(), "%.2f", totalAdded));
                    textViewTotalDeducted.setText("Total Deducted: " + String.format(Locale.getDefault(), "%.2f", totalDeducted));
                }

                @Override
                public void onCancelled(@NonNull DatabaseError databaseError) {
                    Toast.makeText(StatementOfAccountActivity.this, "Failed to load transactions: " + databaseError.getMessage(), Toast.LENGTH_SHORT).show();
                }
            });
        }
    }


    private void printTransactions() {
        StringBuilder htmlContent = new StringBuilder();
        htmlContent.append("<html><body>");
        htmlContent.append("<h1>Transactions Report</h1>");
        htmlContent.append("<table border='1'>");
        htmlContent.append("<tr><th>Date</th><th>Time</th><th>Name</th><th>Amount</th></tr>");

        SimpleDateFormat dateFormat = new SimpleDateFormat("MM/dd/yyyy", Locale.getDefault());
        SimpleDateFormat timeFormat = new SimpleDateFormat("hh:mm a", Locale.getDefault());

        for (Transaction transaction : transactionList) {
            String formattedDate = dateFormat.format(new Date(transaction.getTimestamp()));
            String formattedTime = timeFormat.format(new Date(transaction.getTimestamp()));
            String amount = String.format(Locale.getDefault(), "%.2f", transaction.getAmount());

            htmlContent.append("<tr>");
            htmlContent.append("<td>").append(formattedDate).append("</td>");
            htmlContent.append("<td>").append(formattedTime).append("</td>");
            htmlContent.append("<td>").append(transaction.getName()).append("</td>");
            htmlContent.append("<td>").append(amount).append("</td>");
            htmlContent.append("</tr>");
        }

        htmlContent.append("</table></body></html>");

        // Load HTML content into WebView and print
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                webView.loadUrl("javascript:window.print()");
            }
        });

        webView.loadDataWithBaseURL(null, htmlContent.toString(), "text/html", "UTF-8", null);
    }

    // Custom adapter for transactions
    private class TransactionAdapter extends ArrayAdapter<Transaction> {

        public TransactionAdapter(List<Transaction> transactions) {
            super(StatementOfAccountActivity.this, R.layout.list_item_transaction_receipt, transactions);
        }

        @NonNull
        @Override
        public View getView(int position, View convertView, @NonNull ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(getContext()).inflate(R.layout.list_item_transaction_receipt, parent, false);
            }

            Transaction transaction = getItem(position);
            if (transaction == null) {
                return convertView;
            }

            // Bind data to views in list item layout
            TextView textViewDate = convertView.findViewById(R.id.textViewDate);
            TextView textViewTime = convertView.findViewById(R.id.textViewTime);
            TextView textViewName = convertView.findViewById(R.id.textViewName);
            TextView textViewAccountNumber = convertView.findViewById(R.id.textViewAccountNumber);
            TextView textViewSource = convertView.findViewById(R.id.textViewSource);
            TextView textViewReferenceId = convertView.findViewById(R.id.textViewReferenceId);
            TextView textViewAmount = convertView.findViewById(R.id.textViewAmount);

            // Format date and time from timestamp
            SimpleDateFormat dateFormat = new SimpleDateFormat("MM/dd/yyyy", Locale.getDefault());
            SimpleDateFormat timeFormat = new SimpleDateFormat("hh:mm a", Locale.getDefault());
            Date date = new Date(transaction.getTimestamp());
            String formattedDate = dateFormat.format(date);
            String formattedTime = timeFormat.format(date);

            // Set text size for each view
            textViewDate.setTextSize(8);
            textViewTime.setTextSize(8);
            textViewName.setTextSize(8);
            textViewAccountNumber.setTextSize(7);
            textViewSource.setTextSize(8);
            textViewReferenceId.setTextSize(8);
            textViewAmount.setTextSize(8);

            // Set text for each view
            textViewDate.setText(formattedDate);
            textViewTime.setText(formattedTime);
            textViewName.setText(transaction.getName());
            textViewAccountNumber.setText(transaction.getAccountNumber());
            textViewSource.setText(transaction.getSource());
            textViewReferenceId.setText(transaction.getReferenceId());
            textViewAmount.setText(String.format(Locale.getDefault(), "%.2f", transaction.getAmount()));

            // Return the completed view to render on screen
            return convertView;
        }
    }

    private String getFormattedDateString(Calendar calendar) {
        SimpleDateFormat dateFormat = new SimpleDateFormat("MM/dd/yyyy", Locale.getDefault());
        return dateFormat.format(calendar.getTime());
    }
}
