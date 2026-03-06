package com.example.mysoftpos.viewmodel;

/**
 * Sealed-style state class for login UI.
 * Activity observes this via LiveData and only renders UI — no business logic.
 */
public class LoginState {

    public enum Type { IDLE, LOADING, SUCCESS, ERROR, LOCKED }

    public final Type type;
    public final String message;

    // Success payload
    public final long userId;
    public final String role;
    public final String displayName;
    public final String phone;
    public final String email;

    private LoginState(Type type, String message, long userId, String role,
                       String displayName, String phone, String email) {
        this.type = type;
        this.message = message;
        this.userId = userId;
        this.role = role;
        this.displayName = displayName;
        this.phone = phone;
        this.email = email;
    }

    public static LoginState idle() {
        return new LoginState(Type.IDLE, null, -1, null, null, null, null);
    }

    public static LoginState loading() {
        return new LoginState(Type.LOADING, null, -1, null, null, null, null);
    }

    public static LoginState success(long userId, String role, String displayName,
                                      String phone, String email) {
        return new LoginState(Type.SUCCESS, null, userId, role, displayName, phone, email);
    }

    public static LoginState error(String message) {
        return new LoginState(Type.ERROR, message, -1, null, null, null, null);
    }

    public static LoginState locked(int remainingMinutes) {
        return new LoginState(Type.LOCKED,
                "Account locked. Try again in " + remainingMinutes + " minutes.",
                -1, null, null, null, null);
    }
}

