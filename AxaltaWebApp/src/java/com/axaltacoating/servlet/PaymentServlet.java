package com.axaltacoating.servlet;

import com.axaltacoating.util.DatabaseConnection;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.json.simple.JSONObject;

/**
 * Simple Payment Servlet that marks payments as completed
 */
@WebServlet("/payment/*")
public class PaymentServlet extends HttpServlet {
    private static final Logger LOGGER = Logger.getLogger(PaymentServlet.class.getName());
    
    /**
     * Handles the HTTP GET method - retrieves payment information
     */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        // Check if this is a specific action request
        String action = request.getParameter("action");
        
        if (action != null) {
            // Handle specific actions
            if (action.equals("get")) {
                // Return JSON for AJAX requests
                response.setContentType("application/json");
                Map<String, Object> result = new HashMap<String, Object>();
                result.put("success", true);
                result.put("message", "Payment system is operational");
                
                JSONObject json = new JSONObject(result);
                response.getWriter().write(json.toString());
                return;
            } else if (action.equals("new")) {
                // Handle creating a new payment
                String auctionIdParam = request.getParameter("auctionId");
                String supplierIdParam = request.getParameter("supplierId");
                
                if (auctionIdParam == null || supplierIdParam == null) {
                    request.setAttribute("error", "Missing required parameters");
                    // Retrieve auction winners for the payment page
                    List<Map<String, Object>> auctionWinners = getAuctionWinners();
                    request.setAttribute("auctionWinners", auctionWinners);
                    request.getRequestDispatcher("/WEB-INF/views/payment/list.jsp").forward(request, response);
                    return;
                }
                
                try {
                    long auctionId = Long.parseLong(auctionIdParam);
                    long supplierId = Long.parseLong(supplierIdParam);
                    
                    // Get auction and supplier details
                    Map<String, Object> auction = getAuctionDetails(auctionId);
                    Map<String, Object> supplier = getSupplierDetails(supplierId);
                    
                    if (auction == null || supplier == null) {
                        request.setAttribute("error", "Invalid auction or supplier");
                        // Retrieve auction winners for the payment page
                        List<Map<String, Object>> auctionWinners = getAuctionWinners();
                        request.setAttribute("auctionWinners", auctionWinners);
                        request.getRequestDispatcher("/WEB-INF/views/payment/list.jsp").forward(request, response);
                        return;
                    }
                    
                    // Create empty payment object
                    Map<String, Object> payment = new HashMap<>();
                    payment.put("id", 0);
                    payment.put("amount", auction.get("winning_amount"));
                    payment.put("status", "PENDING");
                    payment.put("paymentMethod", "BANK_TRANSFER");
                    payment.put("paymentDate", new java.util.Date());
                    
                    // Set attributes for the form
                    request.setAttribute("payment", payment);
                    request.setAttribute("auction", auction);
                    request.setAttribute("supplier", supplier);
                    
                    // Forward to payment form
                    request.getRequestDispatcher("/WEB-INF/views/payment/form.jsp").forward(request, response);
                    return;
                } catch (NumberFormatException e) {
                    request.setAttribute("error", "Invalid auction or supplier ID");
                } catch (SQLException e) {
                    LOGGER.log(Level.SEVERE, "Database error while retrieving auction or supplier details", e);
                    request.setAttribute("error", "Database error: " + e.getMessage());
                }
            }
        }
        
        // Retrieve auction winners for the payment page
        List<Map<String, Object>> auctionWinners = getAuctionWinners();
        request.setAttribute("auctionWinners", auctionWinners);
        
        // Retrieve payment history
        List<Map<String, Object>> paymentHistory = getPaymentHistory();
        request.setAttribute("paymentHistory", paymentHistory);
        
        // For regular GET requests, forward to the payments page
        request.getRequestDispatcher("/WEB-INF/views/payment/list.jsp").forward(request, response);
    }

    /**
     * Handles the HTTP POST method - processes payment requests
     */
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String auctionIdParam = request.getParameter("auctionId");
        String supplierIdParam = request.getParameter("supplierId");
        String amountParam = request.getParameter("amount");
        String action = request.getParameter("action");
        
        // Basic validation
        if (auctionIdParam == null || supplierIdParam == null || amountParam == null) {
            if (action != null && action.equals("create")) {
                // Form submission - redirect back to payment list with error
                request.getSession().setAttribute("error", "Missing required parameters");
                response.sendRedirect(request.getContextPath() + "/payment");
            } else {
                // API call - send JSON error
                sendErrorResponse(response, "Missing required parameters");
            }
            return;
        }
        
        try {
            long auctionId = Long.parseLong(auctionIdParam);
            long supplierId = Long.parseLong(supplierIdParam);
            double amount = Double.parseDouble(amountParam);
            
            // Process the payment
            boolean success = markPaymentAsCompleted(auctionId, supplierId, amount);
            
            if (success) {
                // Check if this is a form submission
                if (action != null && action.equals("create")) {
                    // Redirect to payment list with success message
                    request.getSession().setAttribute("success", "Payment has been successfully created");
                    response.sendRedirect(request.getContextPath() + "/payment");
                } else {
                    // Return JSON response for API calls
                    Map<String, Object> result = new HashMap<String, Object>();
                    result.put("success", true);
                    result.put("message", "Payment processed successfully");
                    result.put("auctionId", auctionId);
                    result.put("supplierId", supplierId);
                    result.put("amount", amount);
                    
                    JSONObject json = new JSONObject(result);
                    response.setContentType("application/json");
                    response.getWriter().write(json.toString());
                }
            } else {
                if (action != null && action.equals("create")) {
                    // Redirect to payment list with error message
                    request.getSession().setAttribute("error", "Failed to create payment");
                    response.sendRedirect(request.getContextPath() + "/payment");
                } else {
                    // Send JSON error for API calls
                    sendErrorResponse(response, "Failed to process payment");
                }
            }
        } catch (NumberFormatException e) {
            sendErrorResponse(response, "Invalid parameter format");
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Database error while processing payment", e);
            sendErrorResponse(response, "Database error: " + e.getMessage());
        }
    }
    
    /**
     * Simple method to mark a payment as completed in the database
     */
    /**
     * Creates a payment record and marks it as completed
     */
    private boolean markPaymentAsCompleted(long auctionId, long supplierId, double amount) throws SQLException {
        Connection conn = null;
        PreparedStatement stmt = null;
        boolean success = false;

        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false);
            
            // Insert the payment record
            String sql = "INSERT INTO payments (auction_id, supplier_id, amount, status, payment_date) " +
                         "VALUES (?, ?, ?, 'COMPLETED', NOW())";
            stmt = conn.prepareStatement(sql);
            stmt.setLong(1, auctionId);
            stmt.setLong(2, supplierId);
            stmt.setDouble(3, amount);

            int result = stmt.executeUpdate();
            stmt.close();
            
            // If payment was inserted successfully, just commit the transaction
            if (result > 0) {
                conn.commit();
                success = true;
            } else {
                conn.rollback();
            }
            
            return success;
        } catch (SQLException e) {
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException ex) {
                    // Ignore rollback error
                }
            }
            throw e;
        } finally {
            if (conn != null) {
                try {
                    conn.setAutoCommit(true);
                } catch (SQLException e) {
                    // Ignore
                }
            }
            DatabaseConnection.closeQuietly(null, stmt, conn);
        }
    }
    

    
    /**
     * Helper method to send error responses
     */
    private void sendErrorResponse(HttpServletResponse response, String message) throws IOException {
        Map<String, Object> errorResponse = new HashMap<String, Object>();
        errorResponse.put("success", false);
        errorResponse.put("message", message);
        
        JSONObject json = new JSONObject(errorResponse);
        response.setContentType("application/json");
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        response.getWriter().write(json.toString());
    }
    
    /**
     * Retrieves the list of auction winners for the payment page
     */
    private List<Map<String, Object>> getAuctionWinners() {
        List<Map<String, Object>> winners = new ArrayList<>();
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        
        try {
            conn = DatabaseConnection.getConnection();
            String sql = "SELECT a.id as auction_id, p.name as product_name, a.end_time, " +
                         "a.current_price as winning_amount, s.id as supplier_id, s.name as supplier_name, " +
                         "(SELECT COUNT(*) FROM payments WHERE auction_id = a.id) > 0 as has_payment " +
                         "FROM reverse_auctions a " +
                         "JOIN products p ON a.product_id = p.id " +
                         "JOIN bids b ON b.auction_id = a.id AND b.bid_amount = a.current_price " +
                         "JOIN suppliers s ON b.user_id = s.id " +
                         "WHERE a.status = 'COMPLETED' AND a.end_time < NOW() " +
                         "ORDER BY a.end_time DESC";
            
            stmt = conn.prepareStatement(sql);
            rs = stmt.executeQuery();
            
            while (rs.next()) {
                Map<String, Object> winner = new HashMap<>();
                winner.put("auctionId", rs.getLong("auction_id"));
                winner.put("productName", rs.getString("product_name"));
                winner.put("endTime", rs.getTimestamp("end_time"));
                winner.put("winningAmount", rs.getDouble("winning_amount"));
                winner.put("supplierId", rs.getLong("supplier_id"));
                winner.put("supplierName", rs.getString("supplier_name"));
                winner.put("hasPayment", rs.getBoolean("has_payment"));
                winners.add(winner);
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error retrieving auction winners", e);
        } finally {
            DatabaseConnection.closeQuietly(rs, stmt, conn);
        }
        
        return winners;
    }
    
    /**
     * Retrieves auction details by ID
     */
    private Map<String, Object> getAuctionDetails(long auctionId) throws SQLException {
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        
        try {
            conn = DatabaseConnection.getConnection();
            String sql = "SELECT a.id, a.product_id, p.name as product_name, p.unit, " +
                         "a.current_price as winning_amount, a.end_time " +
                         "FROM reverse_auctions a " +
                         "JOIN products p ON a.product_id = p.id " +
                         "WHERE a.id = ?";
            
            stmt = conn.prepareStatement(sql);
            stmt.setLong(1, auctionId);
            rs = stmt.executeQuery();
            
            if (rs.next()) {
                Map<String, Object> auction = new HashMap<>();
                auction.put("id", rs.getLong("id"));
                auction.put("product_id", rs.getLong("product_id"));
                auction.put("product_name", rs.getString("product_name"));
                auction.put("unit", rs.getString("unit"));
                auction.put("winning_amount", rs.getDouble("winning_amount"));
                auction.put("end_time", rs.getTimestamp("end_time"));
                return auction;
            }
            
            return null;
        } finally {
            DatabaseConnection.closeQuietly(rs, stmt, conn);
        }
    }
    
    /**
     * Retrieves supplier details by ID
     */
    private Map<String, Object> getSupplierDetails(long supplierId) throws SQLException {
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        
        try {
            conn = DatabaseConnection.getConnection();
            String sql = "SELECT id, name, email, phone, status " +
                         "FROM suppliers " +
                         "WHERE id = ?";
            
            stmt = conn.prepareStatement(sql);
            stmt.setLong(1, supplierId);
            rs = stmt.executeQuery();
            
            if (rs.next()) {
                Map<String, Object> supplier = new HashMap<>();
                supplier.put("id", rs.getLong("id"));
                supplier.put("name", rs.getString("name"));
                supplier.put("email", rs.getString("email"));
                supplier.put("phone", rs.getString("phone"));
                supplier.put("status", rs.getString("status"));
                return supplier;
            }
            
            return null;
        } finally {
            DatabaseConnection.closeQuietly(rs, stmt, conn);
        }
    }
    
    /**
     * Retrieves the payment history from the database
     */
    private List<Map<String, Object>> getPaymentHistory() {
        List<Map<String, Object>> payments = new ArrayList<>();
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        
        try {
            conn = DatabaseConnection.getConnection();
            String sql = "SELECT p.id, p.auction_id, p.supplier_id, p.amount, p.status, " +
                         "p.transaction_id, p.payment_method, p.payment_date, p.created_at, " +
                         "a.product_id, pr.name as product_name, " +
                         "s.name as supplier_name " +
                         "FROM payments p " +
                         "JOIN reverse_auctions a ON p.auction_id = a.id " +
                         "JOIN products pr ON a.product_id = pr.id " +
                         "JOIN suppliers s ON p.supplier_id = s.id " +
                         "ORDER BY p.created_at DESC";
            
            stmt = conn.prepareStatement(sql);
            rs = stmt.executeQuery();
            
            while (rs.next()) {
                Map<String, Object> payment = new HashMap<>();
                payment.put("id", rs.getLong("id"));
                payment.put("auctionId", rs.getLong("auction_id"));
                payment.put("supplierId", rs.getLong("supplier_id"));
                payment.put("amount", rs.getDouble("amount"));
                payment.put("status", rs.getString("status"));
                payment.put("transactionId", rs.getString("transaction_id"));
                payment.put("paymentMethod", rs.getString("payment_method"));
                payment.put("paymentDate", rs.getTimestamp("payment_date"));
                payment.put("createdAt", rs.getTimestamp("created_at"));
                payment.put("productName", rs.getString("product_name"));
                payment.put("supplierName", rs.getString("supplier_name"));
                payments.add(payment);
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error retrieving payment history", e);
        } finally {
            DatabaseConnection.closeQuietly(rs, stmt, conn);
        }
        
        return payments;
    }
}
