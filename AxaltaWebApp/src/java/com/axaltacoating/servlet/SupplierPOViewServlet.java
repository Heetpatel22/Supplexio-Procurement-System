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
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

@WebServlet(name = "SupplierPOViewServlet", urlPatterns = {"/supplier-po-view"})
public class SupplierPOViewServlet extends HttpServlet {
    private static final Logger LOGGER = Logger.getLogger(SupplierPOViewServlet.class.getName());

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        Long supplierId = (Long) request.getSession().getAttribute("supplierId");
        if (supplierId == null) {
            response.sendRedirect("login.jsp");
            return;
        }
        List<PurchaseOrderBean> poList = new ArrayList<>();
        String supplierName = null;
        try (Connection conn = DatabaseConnection.getConnection()) {
            // Fetch supplier name for this supplierId
            try (PreparedStatement nameStmt = conn.prepareStatement("SELECT name FROM suppliers WHERE id = ?")) {
                nameStmt.setLong(1, supplierId);
                ResultSet nameRs = nameStmt.executeQuery();
                if (nameRs.next()) {
                    supplierName = nameRs.getString("name");
                }
                nameRs.close();
            }
            try (PreparedStatement stmt = conn.prepareStatement(
                    "SELECT * FROM purchase_orders WHERE supplier_id = ? ORDER BY created_at DESC")) {
                stmt.setLong(1, supplierId);
                ResultSet rs = stmt.executeQuery();
                while (rs.next()) {
                    PurchaseOrderBean po = new PurchaseOrderBean();
                    po.setId(rs.getLong("id"));
                    po.setAuctionId(rs.getLong("auction_id"));
                    po.setMaterial(rs.getString("material"));
                    po.setAmount(rs.getDouble("amount"));
                    po.setQuantity(rs.getInt("quantity"));
                    po.setStatus(rs.getString("status"));
                    po.setCreatedAt(rs.getTimestamp("created_at"));
                    po.setCompanyName(supplierName != null ? supplierName : "Unknown Supplier");
                    // Set supplierId for completeness (not used in JSP, but for consistency)
                    po.setSupplierId(rs.getLong("supplier_id"));
                    po.setBuyerId(rs.getLong("buyer_id"));
                    po.setDeliveryAddress(rs.getString("delivery_address"));
                    po.setPaymentTerms(rs.getString("payment_terms"));
                    po.setDescription(rs.getString("description"));
                    po.setExpectedDeliveryDate(rs.getDate("expected_delivery_date"));
                    po.setIncoterm(rs.getString("incoterm"));
                    po.setIncotermLocation(rs.getString("incoterm_location"));
                    poList.add(po);
                }
                rs.close();
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error loading supplier POs", e);
        }
        request.setAttribute("poList", poList);
        request.getRequestDispatcher("supplier_po.jsp").forward(request, response);
    }

    // Simple bean for PO data
    public static class PurchaseOrderBean {
        private long id;
        private long auctionId;
        private String material;
        private double amount;
        private int quantity;
        private String status;
        private java.util.Date createdAt;
        private String companyName;
        private long supplierId;
        private Long buyerId;
        private String deliveryAddress;
        private String paymentTerms;
        private String description;
        private java.util.Date expectedDeliveryDate;
        private String incoterm;
        private String incotermLocation;

        public long getId() { return id; }
        public void setId(long id) { this.id = id; }
        public long getAuctionId() { return auctionId; }
        public void setAuctionId(long auctionId) { this.auctionId = auctionId; }
        public String getMaterial() { return material; }
        public void setMaterial(String material) { this.material = material; }
        public double getAmount() { return amount; }
        public void setAmount(double amount) { this.amount = amount; }
        public int getQuantity() { return quantity; }
        public void setQuantity(int quantity) { this.quantity = quantity; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public java.util.Date getCreatedAt() { return createdAt; }
        public void setCreatedAt(java.util.Date createdAt) { this.createdAt = createdAt; }
        public String getCompanyName() { return companyName; }
        public void setCompanyName(String companyName) { this.companyName = companyName; }
        public long getSupplierId() { return supplierId; }
        public void setSupplierId(long supplierId) { this.supplierId = supplierId; }
        public Long getBuyerId() { return buyerId; }
        public void setBuyerId(Long buyerId) { this.buyerId = buyerId; }
        public String getDeliveryAddress() { return deliveryAddress; }
        public void setDeliveryAddress(String deliveryAddress) { this.deliveryAddress = deliveryAddress; }
        public String getPaymentTerms() { return paymentTerms; }
        public void setPaymentTerms(String paymentTerms) { this.paymentTerms = paymentTerms; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public java.util.Date getExpectedDeliveryDate() { return expectedDeliveryDate; }
        public void setExpectedDeliveryDate(java.util.Date expectedDeliveryDate) { this.expectedDeliveryDate = expectedDeliveryDate; }
        public String getIncoterm() { return incoterm; }
        public void setIncoterm(String incoterm) { this.incoterm = incoterm; }
        public String getIncotermLocation() { return incotermLocation; }
        public void setIncotermLocation(String incotermLocation) { this.incotermLocation = incotermLocation; }
    }
}
