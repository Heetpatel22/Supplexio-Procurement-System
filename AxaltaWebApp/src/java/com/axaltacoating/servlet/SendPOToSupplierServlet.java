package com.axaltacoating.servlet;

import com.axaltacoating.util.DatabaseConnection;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

@WebServlet(name = "SendPOToSupplierServlet", urlPatterns = {"/send-po-to-supplier"})
public class SendPOToSupplierServlet extends HttpServlet {
    private static final Logger LOGGER = Logger.getLogger(SendPOToSupplierServlet.class.getName());

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String poIdParam = request.getParameter("poId");
        if (poIdParam == null || poIdParam.isEmpty()) {
            response.sendRedirect("supplier_po.jsp?error=Missing+PO+ID");
            return;
        }
        long poId = Long.parseLong(poIdParam);
        try (Connection conn = DatabaseConnection.getConnection()) {
            // Check if PO exists
            boolean exists = false;
            try (PreparedStatement checkStmt = conn.prepareStatement(
                    "SELECT COUNT(*) FROM purchase_orders WHERE id = ?")) {
                checkStmt.setLong(1, poId);
                ResultSet rs = checkStmt.executeQuery();
                if (rs.next() && rs.getInt(1) > 0) {
                    exists = true;
                }
                rs.close();
            }
            if (exists) {
                // Update status to SENT
                try (PreparedStatement stmt = conn.prepareStatement(
                        "UPDATE purchase_orders SET status = 'SENT' WHERE id = ?")) {
                    stmt.setLong(1, poId);
                    int updated = stmt.executeUpdate();
                    if (updated > 0) {
                        response.sendRedirect("supplier_po.jsp?success=PO+sent+to+supplier");
                    } else {
                        response.sendRedirect("supplier_po.jsp?error=PO+not+found");
                    }
                }
            } else {
                // If PO does not exist, collect all required fields from the request and insert it
                String supplierIdParam = request.getParameter("supplierId");
                String material = request.getParameter("material");
                double amount = Double.parseDouble(request.getParameter("amount"));
                int quantity = Integer.parseInt(request.getParameter("quantity"));
                String incoterm = request.getParameter("incoterm");
                String incotermLocation = request.getParameter("incotermLocation");
                // --- Fill missing PO fields with demo data if null/empty ---
                java.util.Random rand = new java.util.Random(System.currentTimeMillis() + poId);
                String unit = request.getParameter("unit");
                if (unit == null || unit.trim().isEmpty()) {
                    String[] units = {"kg", "ton", "pcs", "liters", "meters"};
                    unit = units[rand.nextInt(units.length)];
                }
                String productDescription = request.getParameter("product_description");
                if (productDescription == null || productDescription.trim().isEmpty()) {
                    String[] descs = {"High purity copper sheets", "Bulk commodity order", "Standard industrial material", "Special grade product"};
                    productDescription = descs[rand.nextInt(descs.length)];
                }
                String deliveryAddress = request.getParameter("delivery_address");
                if (deliveryAddress == null || deliveryAddress.trim().isEmpty()) {
                    deliveryAddress = (rand.nextInt(999) + 1) + " Example St, City" + (char)('A' + rand.nextInt(26)) + ", Country";
                }
                String terms = request.getParameter("terms");
                if (terms == null || terms.trim().isEmpty()) {
                    String[] termsArr = {"Net 30", "Net 45", "Advance", "COD"};
                    terms = termsArr[rand.nextInt(termsArr.length)];
                }
                String notes = request.getParameter("notes");
                if (notes == null || notes.trim().isEmpty()) {
                    String[] descs = {"Standard PO for materials", "Urgent delivery required", "Bulk order", "Special instructions apply"};
                    notes = descs[rand.nextInt(descs.length)];
                }
                java.sql.Date deliveryDate = null;
                String deliveryDateStr = request.getParameter("delivery_date");
                if (deliveryDateStr == null || deliveryDateStr.trim().isEmpty()) {
                    deliveryDate = new java.sql.Date(System.currentTimeMillis() + (3 + rand.nextInt(15)) * 24 * 60 * 60 * 1000L);
                } else {
                    try { deliveryDate = java.sql.Date.valueOf(deliveryDateStr); } catch (Exception e) { deliveryDate = new java.sql.Date(System.currentTimeMillis() + (3 + rand.nextInt(15)) * 24 * 60 * 60 * 1000L); }
                }
                // --- Update insert to include all fields ---
                try (PreparedStatement insertStmt = conn.prepareStatement(
                        "INSERT INTO purchase_orders (id, supplier_id, material, amount, quantity, unit, product_description, incoterm, incoterm_location, status, created_at, delivery_address, terms, notes, delivery_date) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'SENT', NOW(), ?, ?, ?, ?)")) {
                    insertStmt.setLong(1, poId);
                    insertStmt.setLong(2, Long.parseLong(supplierIdParam));
                    insertStmt.setString(3, material);
                    insertStmt.setDouble(4, amount);
                    insertStmt.setInt(5, quantity);
                    insertStmt.setString(6, unit);
                    insertStmt.setString(7, productDescription);
                    insertStmt.setString(8, incoterm);
                    insertStmt.setString(9, incotermLocation);
                    insertStmt.setString(10, deliveryAddress);
                    insertStmt.setString(11, terms);
                    insertStmt.setString(12, notes);
                    insertStmt.setDate(13, deliveryDate);
                    int inserted = insertStmt.executeUpdate();
                    if (inserted > 0) {
                        response.sendRedirect("supplier_po.jsp?success=PO+created+and+sent+to+supplier");
                    } else {
                        response.sendRedirect("supplier_po.jsp?error=Failed+to+create+PO");
                    }
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error sending PO to supplier", e);
            response.sendRedirect("supplier_po.jsp?error=Database+error");
        }
    }
}
