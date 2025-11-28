package com.axaltacoating.servlet;

import com.axaltacoating.util.DatabaseConnection;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Servlet for handling auction completion and inventory updates.
 */
@WebServlet(name = "AuctionCompletionServlet", urlPatterns = {"/auction-completion"})
public class AuctionCompletionServlet extends HttpServlet {
    
    private static final Logger LOGGER = Logger.getLogger(AuctionCompletionServlet.class.getName());

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        
        String action = request.getParameter("action");
        
        switch (action) {
            case "completeAuction":
                completeAuction(request, response);
                break;
            case "markDelivered":
                markDelivered(request, response);
                break;
            case "checkCompletedAuctions":
                checkCompletedAuctions(request, response);
                break;
            default:
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid action");
                break;
        }
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        
        String action = request.getParameter("action");
        
        if ("checkCompletedAuctions".equals(action)) {
            checkCompletedAuctions(request, response);
        } else {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid action");
        }
    }

    /**
     * Core logic to complete an auction by ID, used by both servlet and batch completion.
     */
    private boolean completeAuction(long auctionId, Connection conn, String incoterm, String incotermLocation) throws SQLException {
        PreparedStatement stmt = null;
        ResultSet rs = null;
        try {
            conn.setAutoCommit(false);
            LOGGER.info("Starting completeAuction for auctionId=" + auctionId);
            // Get the winning bid (lowest amount)
            stmt = conn.prepareStatement(
                "SELECT b.*, s.name as supplier_name, s.email as supplier_email " +
                "FROM bids b " +
                "JOIN suppliers s ON b.supplier_id = s.id " +
                "WHERE b.auction_id = ? " +
                "ORDER BY b.amount ASC, b.bid_time ASC " +
                "LIMIT 1"
            );
            stmt.setLong(1, auctionId);
            rs = stmt.executeQuery();
            if (rs.next()) {
                long winningSupplierId = rs.getLong("supplier_id");
                double winningAmount = rs.getDouble("amount");
                String supplierName = rs.getString("supplier_name");
                String supplierEmail = rs.getString("supplier_email");
                if (supplierName == null || supplierName.trim().isEmpty()) {
                    // Try to fetch supplier name again from suppliers table
                    PreparedStatement nameStmt = conn.prepareStatement("SELECT name FROM suppliers WHERE id = ?");
                    nameStmt.setLong(1, winningSupplierId);
                    ResultSet nameRs = nameStmt.executeQuery();
                    if (nameRs.next()) {
                        supplierName = nameRs.getString("name");
                    }
                    nameRs.close();
                    nameStmt.close();
                    if (supplierName == null || supplierName.trim().isEmpty()) {
                        supplierName = "Unknown Supplier";
                    }
                }
                if (supplierEmail == null || supplierEmail.trim().isEmpty()) {
                    supplierEmail = "unknown@example.com";
                }
                String supplierCompanyName = supplierName;
                String materialName = null;
                long productId = -1;
                int poQuantity = -1;
                stmt = conn.prepareStatement(
                    "SELECT p.id as product_id, p.name as product_name, ra.required_quantity as auction_quantity FROM reverse_auctions ra JOIN products p ON ra.product_id = p.id WHERE ra.id = ?"
                );
                stmt.setLong(1, auctionId);
                ResultSet rsProduct = stmt.executeQuery();
                if (rsProduct.next()) {
                    productId = rsProduct.getLong("product_id");
                    materialName = rsProduct.getString("product_name");
                    poQuantity = rsProduct.getInt("auction_quantity");
                } else {
                    materialName = "Unknown";
                }
                rsProduct.close();
                if (poQuantity <= 0) {
                    poQuantity = 1;
                }
                stmt = conn.prepareStatement(
                    "UPDATE reverse_auctions " +
                    "SET status = 'COMPLETED', current_price = ?, winning_supplier_id = ? " +
                    "WHERE id = ?"
                );
                stmt.setDouble(1, winningAmount);
                stmt.setLong(2, winningSupplierId);
                stmt.setLong(3, auctionId);
                stmt.executeUpdate();
                stmt = conn.prepareStatement(
                    "INSERT INTO auction_deliveries (auction_id, supplier_id, winning_amount, status, created_at) " +
                    "VALUES (?, ?, ?, 'PENDING', NOW())"
                );
                stmt.setLong(1, auctionId);
                stmt.setLong(2, winningSupplierId);
                stmt.setDouble(3, winningAmount);
                stmt.executeUpdate();
                long nextPONumber = 1;
                PreparedStatement poNumStmt = conn.prepareStatement("SELECT MAX(po_number) FROM purchase_orders");
                ResultSet poNumRs = poNumStmt.executeQuery();
                if (poNumRs.next()) {
                    long maxPONum = poNumRs.getLong(1);
                    if (!poNumRs.wasNull()) {
                        nextPONumber = maxPONum + 1;
                    }
                }
                poNumRs.close();
                poNumStmt.close();
                stmt = conn.prepareStatement("SELECT email FROM suppliers WHERE id = ?");
                stmt.setLong(1, winningSupplierId);
                ResultSet rsEmail = stmt.executeQuery();
                if (rsEmail.next()) {
                    supplierEmail = rsEmail.getString("email");
                }
                rsEmail.close();
                if (supplierEmail == null || supplierEmail.trim().isEmpty()) {
                    supplierEmail = "unknown@example.com";
                }
                double unitPrice = (poQuantity > 0) ? (winningAmount / poQuantity) : 0.0;
                double totalAmount = winningAmount;
                double taxAmount = totalAmount * 0.075;
                double grandTotal = totalAmount + taxAmount;
                // Use demo data for missing PO fields
                java.util.Random rand = new java.util.Random(System.currentTimeMillis() + auctionId);
                String[] incotermsArr = {"DAP", "EXW", "CIF", "FOB"};
                String[] incotermLocationsArr = {"Los Angeles", "Hamburg", "Rotterdam", "Shanghai"};
                if (incoterm == null || incoterm.trim().isEmpty()) {
                    incoterm = incotermsArr[rand.nextInt(incotermsArr.length)];
                }
                if (incotermLocation == null || incotermLocation.trim().isEmpty()) {
                    incotermLocation = incotermLocationsArr[rand.nextInt(incotermLocationsArr.length)];
                }
                String[] units = {"kg", "ton", "pcs", "liters", "meters"};
                String unit = units[rand.nextInt(units.length)];
                String[] descs = {"High purity copper sheets", "Bulk commodity order", "Standard industrial material", "Special grade product"};
                String productDescription = descs[rand.nextInt(descs.length)];
                String deliveryAddress = (rand.nextInt(999) + 1) + " Example St, City" + (char)('A' + rand.nextInt(26)) + ", Country";
                String[] termsArr = {"Net 30", "Net 45", "Advance", "COD"};
                String terms = termsArr[rand.nextInt(termsArr.length)];
                String notes = descs[rand.nextInt(descs.length)];
                java.sql.Date deliveryDate = new java.sql.Date(System.currentTimeMillis() + (3 + rand.nextInt(15)) * 24 * 60 * 60 * 1000L);
                // Check if a PO already exists for this auction and supplier
                boolean poExists = false;
                stmt = conn.prepareStatement(
                    "SELECT COUNT(*) FROM purchase_orders WHERE auction_id = ? AND supplier_id = ?"
                );
                stmt.setLong(1, auctionId);
                stmt.setLong(2, winningSupplierId);
                ResultSet poCheckRs = stmt.executeQuery();
                if (poCheckRs.next() && poCheckRs.getInt(1) > 0) {
                    poExists = true;
                }
                poCheckRs.close();
                stmt.close();
                if (!poExists) {
                    stmt = conn.prepareStatement(
                        "INSERT INTO purchase_orders (po_number, auction_id, supplier_id, supplier_name, supplier_email, company_name, material, amount, status, created_at, delivery_address, incoterm, incoterm_location, product_id, product_name, quantity, unit_price, total_amount, unit, delivery_date, notes, terms, product_description, tax_amount, grand_total) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
                    );
                    stmt.setLong(1, nextPONumber);
                    stmt.setLong(2, auctionId);
                    stmt.setLong(3, winningSupplierId);
                    stmt.setString(4, supplierName);
                    stmt.setString(5, supplierEmail);
                    stmt.setString(6, supplierCompanyName);
                    stmt.setString(7, materialName);
                    stmt.setDouble(8, winningAmount);
                    stmt.setString(9, "PENDING");
                    stmt.setTimestamp(10, new java.sql.Timestamp(System.currentTimeMillis()));
                    stmt.setString(11, deliveryAddress);
                    stmt.setString(12, incoterm); // incoterm
                    stmt.setString(13, incotermLocation); // incotermLocation
                    stmt.setLong(14, productId);
                    stmt.setString(15, materialName);
                    stmt.setInt(16, poQuantity);
                    stmt.setDouble(17, unitPrice);
                    stmt.setDouble(18, totalAmount);
                    stmt.setString(19, unit);
                    stmt.setDate(20, deliveryDate);
                    stmt.setString(21, notes);
                    stmt.setString(22, terms);
                    stmt.setString(23, productDescription);
                    stmt.setDouble(24, taxAmount);
                    stmt.setDouble(25, grandTotal);
                    stmt.executeUpdate();
                }
                conn.commit();
                return true;
            } else {
                conn.rollback();
                return false;
            }
        } catch (SQLException e) {
            if (conn != null) conn.rollback();
            throw e;
        } finally {
            if (rs != null) rs.close();
            if (stmt != null) stmt.close();
        }
    }

    /**
     * Completes an auction and determines the winning bid.
     */
    private void completeAuction(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String auctionIdParam = request.getParameter("auctionId");
        String incoterm = request.getParameter("incoterm");
        String incotermLocation = request.getParameter("incotermLocation");
        if (auctionIdParam == null || auctionIdParam.isEmpty()) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.getWriter().write("{\"success\": false, \"message\": \"Auction ID is required\"}");
            return;
        }
        long auctionId = Long.parseLong(auctionIdParam);
        Connection conn = null;
        try {
            conn = DatabaseConnection.getConnection();
            boolean success = completeAuction(auctionId, conn, incoterm, incotermLocation);
            if (success) {
                response.setContentType("application/json");
                response.getWriter().write(String.format(
                    "{\"success\": true, \"message\": \"Auction completed successfully.\"}"
                ));
            } else {
                response.setContentType("application/json");
                response.getWriter().write("{\"success\": false, \"message\": \"No bids found for this auction\"}");
            }
        } catch (SQLException e) {
            response.setContentType("application/json");
            response.getWriter().write("{\"success\": false, \"message\": \"Error completing auction: " + e.getMessage() + "\"}");
        } finally {
            try { if (conn != null) conn.close(); } catch (SQLException e) { }
        }
    }

    /**
     * Marks an auction delivery as completed and updates inventory.
     */
    private void markDelivered(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        
        String auctionIdParam = request.getParameter("auctionId");
        String quantityParam = request.getParameter("quantity");
        
        if (auctionIdParam == null || auctionIdParam.isEmpty() || 
            quantityParam == null || quantityParam.isEmpty()) {
            response.setContentType("application/json");
            response.getWriter().write("{\"success\": false, \"message\": \"Auction ID and quantity are required\"}");
            return;
        }
        
        long auctionId = Long.parseLong(auctionIdParam);
        int deliveredQuantity = Integer.parseInt(quantityParam);
        
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        
        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false);
            
            // Get auction and product details
            stmt = conn.prepareStatement(
                "SELECT ra.product_id, p.name as product_name, p.inventory_id, ad.supplier_id " +
                "FROM reverse_auctions ra " +
                "JOIN products p ON ra.product_id = p.id " +
                "JOIN auction_deliveries ad ON ra.id = ad.auction_id " +
                "WHERE ra.id = ? AND ad.status = 'PENDING'"
            );
            stmt.setLong(1, auctionId);
            rs = stmt.executeQuery();
            
            if (rs.next()) {
                long productId = rs.getLong("product_id");
                String productName = rs.getString("product_name");
                Long inventoryId = rs.getLong("inventory_id");
                
                // Update inventory if inventory_id exists
                if (inventoryId != null && inventoryId > 0) {
                    stmt = conn.prepareStatement(
                        "UPDATE inventory SET quantity = quantity + ?, updated_at = NOW() WHERE id = ?"
                    );
                    stmt.setInt(1, deliveredQuantity);
                    stmt.setLong(2, inventoryId);
                    int inventoryUpdated = stmt.executeUpdate();
                    
                    if (inventoryUpdated > 0) {
                        // Update kanban status based on new quantity
                        stmt = conn.prepareStatement(
                            "UPDATE inventory " +
                            "SET kanban_status = CASE " +
                            "    WHEN quantity <= min_threshold THEN 'Low' " +
                            "    WHEN quantity >= max_threshold THEN 'High' " +
                            "    ELSE 'Medium' " +
                            "END, " +
                            "needs_auction = (quantity <= min_threshold), " +
                            "auction_started = FALSE " +
                            "WHERE id = ?"
                        );
                        stmt.setLong(1, inventoryId);
                        stmt.executeUpdate();
                    }
                }
                
                // Update product stock quantity
                stmt = conn.prepareStatement(
                    "UPDATE products SET stock_quantity = stock_quantity + ? WHERE id = ?"
                );
                stmt.setInt(1, deliveredQuantity);
                stmt.setLong(2, productId);
                stmt.executeUpdate();
                
                // Mark delivery as completed
                stmt = conn.prepareStatement(
                    "UPDATE auction_deliveries SET status = 'DELIVERED', delivered_quantity = ?, delivery_date = NOW() WHERE auction_id = ?"
                );
                stmt.setInt(1, deliveredQuantity);
                stmt.setLong(2, auctionId);
                stmt.executeUpdate();
                
                conn.commit();
                
                LOGGER.log(Level.INFO, "Delivery completed for auction {0}. Added {1} units of {2} to inventory", 
                    new Object[]{auctionId, deliveredQuantity, productName});
                
                response.setContentType("application/json");
                response.getWriter().write(String.format(
                    "{\"success\": true, \"message\": \"Delivery marked as completed. Added %d units of %s to inventory\"}",
                    deliveredQuantity, productName
                ));
                
            } else {
                conn.rollback();
                response.setContentType("application/json");
                response.getWriter().write("{\"success\": false, \"message\": \"No pending delivery found for this auction\"}");
            }
            
        } catch (SQLException e) {
            try {
                if (conn != null) conn.rollback();
            } catch (SQLException ex) {
                LOGGER.log(Level.SEVERE, "Error rolling back transaction", ex);
            }
            
            LOGGER.log(Level.SEVERE, "Error marking delivery as completed", e);
            response.setContentType("application/json");
            response.getWriter().write("{\"success\": false, \"message\": \"Error updating delivery: " + e.getMessage() + "\"}");
            
        } finally {
            try {
                if (rs != null) rs.close();
                if (stmt != null) stmt.close();
                if (conn != null) {
                    conn.setAutoCommit(true);
                    conn.close();
                }
            } catch (SQLException e) {
                LOGGER.log(Level.SEVERE, "Error closing database resources", e);
            }
        }
    }

    /**
     * Checks for auctions that have ended and automatically completes them.
     */
    private void checkCompletedAuctions(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        try {
            conn = DatabaseConnection.getConnection();
            stmt = conn.prepareStatement(
                "SELECT id FROM reverse_auctions " +
                "WHERE status = 'ACTIVE' AND end_time < NOW()"
            );
            rs = stmt.executeQuery();
            int completedCount = 0;
            while (rs.next()) {
                long auctionId = rs.getLong("id");
                boolean success = completeAuction(auctionId, conn, null, null);
                if (success) completedCount++;
            }
            response.setContentType("application/json");
            response.getWriter().write(String.format(
                "{\"success\": true, \"message\": \"Checked and completed %d auctions\", \"completedCount\": %d}",
                completedCount, completedCount
            ));
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error checking completed auctions", e);
            response.setContentType("application/json");
            response.getWriter().write("{\"success\": false, \"message\": \"Error checking completed auctions: " + e.getMessage() + "\"}");
        } finally {
            try {
                if (rs != null) rs.close();
                if (stmt != null) stmt.close();
                if (conn != null) conn.close();
            } catch (SQLException e) {
                LOGGER.log(Level.SEVERE, "Error closing database resources", e);
            }
        }
    }
}