package com.neuralarc.ui;

enum PortfolioCaptureAutomationState {
    MONITORING,
    CLEANING_PENDING_ORDERS,
    CAPTURING,
    WAITING_FOR_CONFIRMATION,
    REENTERING_POSITIONS,
    RESTARTING_MONITORING,
    STOPPED,
    ERROR
}
