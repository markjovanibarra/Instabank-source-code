package com.example.instabank;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.Random;

public class PaymayaPay extends AppCompatActivity {

    private EditText editTextName, editTextAccountNumber, editTextAmount;
    private DatabaseReference usersRef;
    private FirebaseUser currentUser;
    private TextView textViewCurrentBalance;

    private static final double MIN_BALANCE_AFTER_TRANSACTION = 100.0; // New constant for minimum balance



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_paymaya_pay);

        // Initialize Firebase
        usersRef = FirebaseDatabase.getInstance().getReference().child("users");
        currentUser = FirebaseAuth.getInstance().getCurrentUser();

        // Initialize UI elements
        editTextName = findViewById(R.id.editTextName);
        editTextAccountNumber = findViewById(R.id.editTextAccountNumber);
        editTextAmount = findViewById(R.id.editTextAmount);
        Button buttonPay = findViewById(R.id.buttonPay);
        textViewCurrentBalance = findViewById(R.id.textViewCurrentBalance);

        // Fetch and display current balance
        if (currentUser != null) {
            String currentUserPhoneNumber = currentUser.getPhoneNumber();
            if (currentUserPhoneNumber != null) {
                usersRef.orderByChild("phoneNumber").equalTo(currentUserPhoneNumber)
                        .addListenerForSingleValueEvent(new ValueEventListener() {
                            @Override
                            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                                if (dataSnapshot.exists()) {
                                    for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                                        Double currentBalance = snapshot.child("balance").getValue(Double.class);
                                        if (currentBalance != null) {
                                            textViewCurrentBalance.setText("Current Balance: ₱" + String.format("%.2f", currentBalance));
                                        } else {
                                            textViewCurrentBalance.setText("Current Balance: ₱0.00");
                                        }
                                    }
                                }
                            }

                            @Override
                            public void onCancelled(@NonNull DatabaseError databaseError) {
                                Toast.makeText(PaymayaPay.this, "Failed to fetch balance: " + databaseError.getMessage(), Toast.LENGTH_SHORT).show();
                            }
                        });
            }
        }

        // Set click listener for Pay button
        buttonPay.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Validate inputs
                String name = editTextName.getText().toString().trim();
                String accountNumber = editTextAccountNumber.getText().toString().trim();
                String amountString = editTextAmount.getText().toString().trim();

                // Validate name input
                if (name.isEmpty()) {
                    editTextName.setError("Name is required");
                    editTextName.requestFocus();
                    return;
                } else if (!isValidName(name)) {
                    // Show dialog for invalid name format
                    showInvalidNameDialog();
                    return;
                }

                // Validate account number
                if (accountNumber.isEmpty()) {
                    editTextAccountNumber.setError("Account number is required");
                    editTextAccountNumber.requestFocus();
                    return;
                }

                // Validate amount
                if (amountString.isEmpty()) {
                    editTextAmount.setError("Amount is required");
                    editTextAmount.requestFocus();
                    return;
                } else if (!isValidAmount(amountString)) {
                    // Show dialog for invalid amount format
                    showInvalidAmountDialog();
                    return;
                }

                // Convert amount to double
                double amount = Double.parseDouble(amountString);

                // Validate maximum amount
                if (amount > 50000) {
                    showMaxAmountDialog();
                    return;
                }

                // Validate minimum amount
                if (amount < 10) {
                    showMinAmountDialog();
                    return;
                }

                // Deduct amount from user's balance in Firebase
                if (currentUser != null) {
                    String phoneNumber = currentUser.getPhoneNumber();
                    if (phoneNumber != null) {
                        usersRef.orderByChild("phoneNumber").equalTo(phoneNumber)
                                .addListenerForSingleValueEvent(new ValueEventListener() {
                                    @Override
                                    public void onDataChange(DataSnapshot dataSnapshot) {
                                        if (dataSnapshot.exists()) {
                                            for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                                                Double currentBalance = snapshot.child("balance").getValue(Double.class);
                                                if (currentBalance != null) {
                                                    if (currentBalance >= amount) {
                                                        double newBalance = currentBalance - amount;
                                                        // Check if the new balance is below the minimum required balance
                                                        if (newBalance < MIN_BALANCE_AFTER_TRANSACTION) {
                                                            showMinimumBalanceDialog();
                                                            return;
                                                        }
                                                        // Update balance in Firebase
                                                        snapshot.getRef().child("balance").setValue(newBalance)
                                                                .addOnSuccessListener(new OnSuccessListener<Void>() {
                                                                    @Override
                                                                    public void onSuccess(Void aVoid) {
                                                                        // Generate a unique reference ID
                                                                        String referenceId = generateReferenceId();

                                                                        // Record transaction with server timestamp
                                                                        recordTransaction(name, accountNumber, -amount, "PayMaya", referenceId);

                                                                        // Update balance on dashboard after successful payment
                                                                        updateDashboardBalance(newBalance);

                                                                        // Calculate and add rewards
                                                                        double rewardAmount = amount * 0.001;
                                                                        addRewardsToUser(rewardAmount);

                                                                        Toast.makeText(PaymayaPay.this, "Payment of ₱" + amount + " successful", Toast.LENGTH_SHORT).show();

                                                                        // Show the receipt
                                                                        showReceipt(name, accountNumber, amount, "PayMaya", referenceId);

                                                                        // Finish activity
                                                                        finish();
                                                                    }
                                                                })
                                                                .addOnFailureListener(new OnFailureListener() {
                                                                    @Override
                                                                    public void onFailure(@NonNull Exception e) {
                                                                        Toast.makeText(PaymayaPay.this, "Failed to deduct balance: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                                                                    }
                                                                });
                                                    } else {
                                                        showInsufficientBalanceDialog();                                                    }
                                                }
                                            }
                                        } else {
                                            Toast.makeText(PaymayaPay.this, "User not found", Toast.LENGTH_SHORT).show();
                                        }
                                    }

                                    @Override
                                    public void onCancelled(DatabaseError databaseError) {
                                        Toast.makeText(PaymayaPay.this, "Failed to deduct balance: " + databaseError.getMessage(), Toast.LENGTH_SHORT).show();
                                    }
                                });
                    }
                }
            }
        });
    }

    private boolean isValidName(String name) {
        // Check for invalid characters (symbols and dots)
        String regex = "^[a-zA-Z\\s\\-]*$";
        return name.matches(regex);
    }

    private boolean isValidAmount(String amount) {
        // Check for invalid characters (dots and symbols)
        String regex = "^[0-9]+$";
        return amount.matches(regex);
    }

    private void showInvalidNameDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Invalid Name Format");
        builder.setMessage("Name should only contain letters, spaces, and hyphens.");
        builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
                editTextName.requestFocus();
            }
        });
        AlertDialog dialog = builder.create();
        dialog.show();
    }

    // Method to show dialog for maximum amount exceeded
    private void showMaxAmountDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Maximum Amount Exceeded");
        builder.setMessage("The maximum amount for payment is ₱50,000.");
        builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
                editTextAmount.requestFocus();
            }
        });
        AlertDialog dialog = builder.create();
        dialog.show();
    }

    // Method to show dialog for minimum amount not met
    private void showMinAmountDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Minimum Amount Not Met");
        builder.setMessage("The minimum amount for payment is ₱10.");
        builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
                editTextAmount.requestFocus();
            }
        });
        AlertDialog dialog = builder.create();
        dialog.show();
    }

    private void showInvalidAmountDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Invalid Amount Format");
        builder.setMessage("Amount should only contain numeric digits.");
        builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
                editTextAmount.requestFocus();
            }
        });
        AlertDialog dialog = builder.create();
        dialog.show();
    }

    private void showInsufficientBalanceDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Insufficient Balance");
        builder.setMessage("You do not have enough balance to make this payment.");
        builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });
        AlertDialog dialog = builder.create();
        dialog.show();
    }


    private String generateReferenceId() {
        Random random = new Random();
        long referenceId = 100000000000L + (long)(random.nextDouble() * 900000000000L); // Ensure a 12-digit random number
        return String.valueOf(referenceId);
    }

    private void recordTransaction(String name, String accountNumber, double amount, String source, String referenceId) {
        // Create a new transaction entry in Firebase with server timestamp
        DatabaseReference transactionsRef = FirebaseDatabase.getInstance().getReference()
                .child("users")
                .child(currentUser.getUid())
                .child("transactions");

        String transactionId = transactionsRef.push().getKey();
        if (transactionId != null) {
            long timestamp = System.currentTimeMillis(); // Current timestamp
            Transaction transaction = new Transaction(name, accountNumber, amount, source, timestamp, referenceId);
            transactionsRef.child(transactionId).setValue(transaction);
        }
    }

    private void showMinimumBalanceDialog() {
        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(this);
        builder.setTitle("Minimum Balance Requirement");
        builder.setMessage("A minimum balance of ₱" + MIN_BALANCE_AFTER_TRANSACTION + " must be maintained after the transaction.");
        builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });
        builder.show();
    }

    private void updateDashboardBalance(double newBalance) {
        Intent resultIntent = new Intent();
        resultIntent.putExtra("newBalance", newBalance);
        setResult(RESULT_OK, resultIntent);
    }

    private void showReceipt(String name, String accountNumber, double amount, String source, String referenceId) {
        Intent intent = new Intent(this, ReceiptActivity.class);
        intent.putExtra("name", name);
        intent.putExtra("accountNumber", accountNumber);
        intent.putExtra("amount", amount);
        intent.putExtra("source", source);
        long timestamp = System.currentTimeMillis(); // Use current timestamp for the receipt
        intent.putExtra("timestamp", timestamp);
        intent.putExtra("referenceId", referenceId);
        startActivity(intent);
    }

    private void addRewardsToUser(double rewardAmount) {
        usersRef.orderByChild("phoneNumber").equalTo(currentUser.getPhoneNumber())
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                        for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                            Double currentRewards = snapshot.child("rewards").getValue(Double.class);
                            if (currentRewards == null) {
                                currentRewards = 0.0;
                            }
                            double newRewards = currentRewards + rewardAmount;
                            snapshot.getRef().child("rewards").setValue(newRewards).addOnCompleteListener(task -> {
                                if (task.isSuccessful()) {
                                    Toast.makeText(PaymayaPay.this, "Rewards updated successfully", Toast.LENGTH_SHORT).show();
                                } else {
                                    Toast.makeText(PaymayaPay.this, "Failed to update rewards: " + task.getException().getMessage(), Toast.LENGTH_SHORT).show();
                                }
                            });
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Toast.makeText(PaymayaPay.this, "Failed to update rewards: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }
}
