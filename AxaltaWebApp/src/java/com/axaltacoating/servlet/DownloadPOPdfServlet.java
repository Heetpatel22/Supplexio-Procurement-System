package com.axaltacoating.servlet;

import com.axaltacoating.util.DatabaseConnection;
import com.itextpdf.text.*;
import com.itextpdf.text.pdf.PdfPCell;
import com.itextpdf.text.pdf.PdfPTable;
import com.itextpdf.text.pdf.PdfWriter;

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

@WebServlet(name = "DownloadPOPdfServlet", urlPatterns = {"/download-po-pdf"})
public class DownloadPOPdfServlet extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        String poIdParam = request.getParameter("poId");
        if (poIdParam == null || poIdParam.isEmpty()) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Missing PO ID");
            return;
        }
        long poId = Long.parseLong(poIdParam);
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT po.*, s.name as supplier_name, s.email as supplier_email FROM purchase_orders po LEFT JOIN suppliers s ON po.supplier_id = s.id WHERE po.id = ?")) {
            stmt.setLong(1, poId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                response.setContentType("application/pdf");
                response.setHeader("Content-Disposition", "attachment; filename=PO-" + poId + ".pdf");
                Document document = new Document();
                PdfWriter.getInstance(document, response.getOutputStream());
                document.open();
                // --- HEADER ---
                Font titleFont = new Font(Font.FontFamily.HELVETICA, 22, Font.BOLD, BaseColor.BLUE);
                Paragraph title = new Paragraph("SUPPLEXIO", titleFont);
                title.setAlignment(Element.ALIGN_LEFT);
                document.add(title);
                document.add(new Paragraph("\n"));
                // Add a colored header bar
                PdfPTable headerBar = new PdfPTable(1);
                headerBar.setWidthPercentage(100);
                PdfPCell headerCell = new PdfPCell(new Phrase("PURCHASE ORDER", new Font(Font.FontFamily.HELVETICA, 18, Font.BOLD, BaseColor.WHITE)));
                headerCell.setBackgroundColor(new BaseColor(255, 87, 34)); // Orange
                headerCell.setHorizontalAlignment(Element.ALIGN_CENTER);
                headerCell.setBorder(Rectangle.NO_BORDER);
                headerCell.setPadding(12f);
                headerBar.addCell(headerCell);
                document.add(headerBar);
                document.add(new Paragraph("\n"));
                // Add company logo placeholder
                Paragraph logo = new Paragraph("", new Font(Font.FontFamily.HELVETICA, 16, Font.BOLD, new BaseColor(0, 153, 204)));
                logo.setAlignment(Element.ALIGN_LEFT);
                document.add(logo);
                document.add(new Paragraph("\n"));
                // --- PO Info ---
                PdfPTable infoTable = new PdfPTable(2);
                infoTable.setWidthPercentage(100);
                infoTable.addCell(getCell("Purchase Order", PdfPCell.ALIGN_LEFT, Font.BOLD));
                infoTable.addCell(getCell("PO No: " + rs.getString("po_number") + "\n" + rs.getTimestamp("created_at"), PdfPCell.ALIGN_RIGHT, Font.NORMAL));
                document.add(infoTable);
                document.add(new Paragraph("\n"));
                // --- Supplier/Delivery ---
                PdfPTable supTable = new PdfPTable(2);
                supTable.setWidthPercentage(100);
                supTable.addCell(getCell("SUPPLIER", PdfPCell.ALIGN_CENTER, Font.BOLD));
                supTable.addCell(getCell("DELIVERY ADDRESS", PdfPCell.ALIGN_CENTER, Font.BOLD));
                String supplierBlock = rs.getString("supplier_name") +
                        (rs.getString("company_name") != null ? "\n" + rs.getString("company_name") : "") +
                        (rs.getString("supplier_address") != null ? "\n" + rs.getString("supplier_address") : "") +
                        (rs.getString("supplier_phone") != null ? "\nPhone: " + rs.getString("supplier_phone") : "") +
                        (rs.getString("supplier_email") != null ? "\nEmail: " + rs.getString("supplier_email") : "");
                String deliveryBlock = (rs.getString("delivery_address") != null && !rs.getString("delivery_address").isEmpty())
                        ? rs.getString("delivery_address")
                        : "Supplexio HQ\n2001 Market Street, Suite 3600\nPhiladelphia, PA 19103";
                supTable.addCell(getCell(supplierBlock, PdfPCell.ALIGN_LEFT, Font.NORMAL));
                supTable.addCell(getCell(deliveryBlock, PdfPCell.ALIGN_LEFT, Font.NORMAL));
                document.add(supTable);
                document.add(new Paragraph("\n"));
                // --- Items Table ---
                PdfPTable itemTable = new PdfPTable(6);
                itemTable.setWidthPercentage(100);
                itemTable.addCell(getCell("ITEM NAME", PdfPCell.ALIGN_CENTER, Font.BOLD));
                itemTable.addCell(getCell("DESCRIPTION", PdfPCell.ALIGN_CENTER, Font.BOLD));
                itemTable.addCell(getCell("QTY", PdfPCell.ALIGN_CENTER, Font.BOLD));
                itemTable.addCell(getCell("UNIT", PdfPCell.ALIGN_CENTER, Font.BOLD));
                itemTable.addCell(getCell("UNIT PRICE", PdfPCell.ALIGN_CENTER, Font.BOLD));
                itemTable.addCell(getCell("TOTAL", PdfPCell.ALIGN_CENTER, Font.BOLD));
                itemTable.addCell(getCell(rs.getString("product_name"), PdfPCell.ALIGN_LEFT, Font.NORMAL));
                itemTable.addCell(getCell(rs.getString("product_description") != null ? rs.getString("product_description") : "-", PdfPCell.ALIGN_LEFT, Font.NORMAL));
                int qty = rs.getInt("quantity");
                String unit = rs.getString("unit") != null ? rs.getString("unit") : "-";
                double unitPrice = rs.getDouble("unit_price");
                double lineTotal = rs.getDouble("total_amount");
                itemTable.addCell(getCell(String.valueOf(qty), PdfPCell.ALIGN_CENTER, Font.NORMAL));
                itemTable.addCell(getCell(unit, PdfPCell.ALIGN_CENTER, Font.NORMAL));
                itemTable.addCell(getCell(String.format("$%.2f", unitPrice), PdfPCell.ALIGN_RIGHT, Font.NORMAL));
                itemTable.addCell(getCell(String.format("$%.2f", lineTotal), PdfPCell.ALIGN_RIGHT, Font.NORMAL));
                document.add(itemTable);
                document.add(new Paragraph("\n"));
                // --- Order Total ---
                double tax = rs.getDouble("tax_amount");
                double grandTotal = rs.getDouble("grand_total");
                Paragraph totalPara = new Paragraph("" + String.format("", grandTotal), new Font(Font.FontFamily.HELVETICA, 14, Font.BOLD, BaseColor.BLUE));
                totalPara.setAlignment(Element.ALIGN_RIGHT);
                document.add(totalPara);
                // --- Shipping Terms, Method, Delivery Date ---
                PdfPTable shipping = new PdfPTable(4);
                shipping.setWidthPercentage(100);
                shipping.addCell(getCell("INCOTERM", PdfPCell.ALIGN_CENTER, Font.BOLD));
                shipping.addCell(getCell("INCOTERM LOCATION", PdfPCell.ALIGN_CENTER, Font.BOLD));
                shipping.addCell(getCell("TERMS", PdfPCell.ALIGN_CENTER, Font.BOLD));
                shipping.addCell(getCell("DELIVERY DATE", PdfPCell.ALIGN_CENTER, Font.BOLD));
                shipping.addCell(getCell(rs.getString("incoterm") != null ? rs.getString("incoterm") : "-", PdfPCell.ALIGN_LEFT, Font.NORMAL));
                shipping.addCell(getCell(rs.getString("incoterm_location") != null ? rs.getString("incoterm_location") : "-", PdfPCell.ALIGN_LEFT, Font.NORMAL));
                shipping.addCell(getCell(rs.getString("terms") != null ? rs.getString("terms") : "-", PdfPCell.ALIGN_LEFT, Font.NORMAL));
                java.sql.Date delDate = rs.getDate("delivery_date");
                String delDateStr = (delDate != null) ? new java.text.SimpleDateFormat("dd/MM/yyyy").format(delDate) : "-";
                shipping.addCell(getCell(delDateStr, PdfPCell.ALIGN_LEFT, Font.NORMAL));
                document.add(shipping);
                document.add(new Paragraph("\n"));
                // Comments/Notes from PO
                String notes = rs.getString("notes");
                document.add(new Paragraph("Comments or Special Instructions:", new Font(Font.FontFamily.HELVETICA, 10, Font.BOLD, BaseColor.BLACK)));
                document.add(new Paragraph(notes != null ? notes : "-"));
                document.add(new Paragraph("\n"));
                // Style for totals
                PdfPTable totals = new PdfPTable(2);
                totals.setWidthPercentage(40);
                totals.setHorizontalAlignment(Element.ALIGN_RIGHT);
                totals.addCell(getStyledCell("SUBTOTAL", PdfPCell.ALIGN_LEFT, true));
                totals.addCell(getStyledCell(String.format("$%.2f", rs.getDouble("amount")), PdfPCell.ALIGN_RIGHT, false));
                totals.addCell(getStyledCell("TAX", PdfPCell.ALIGN_LEFT, true));
                totals.addCell(getStyledCell(String.format("$%.2f", tax), PdfPCell.ALIGN_RIGHT, false));
                totals.addCell(getStyledCell("TOTAL", PdfPCell.ALIGN_LEFT, true));
                totals.addCell(getStyledCell(String.format("$%.2f", grandTotal), PdfPCell.ALIGN_RIGHT, false));
                document.add(totals);
                document.close();
            } else {
                response.sendError(HttpServletResponse.SC_NOT_FOUND, "PO not found");
            }
        } catch (Exception e) {
            throw new ServletException(e);
        }
    }
    private PdfPCell getCell(String text, int alignment, int style) {
        Font font = new Font(Font.FontFamily.HELVETICA, 10, style);
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setPadding(5);
        cell.setHorizontalAlignment(alignment);
        return cell;
    }
    private PdfPCell getStyledCell(String text, int alignment, boolean isHeader) {
        Font font = new Font(Font.FontFamily.HELVETICA, 10, isHeader ? Font.BOLD : Font.NORMAL);
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setPadding(5);
        cell.setHorizontalAlignment(alignment);
        if (isHeader) {
            cell.setBackgroundColor(new BaseColor(221, 221, 221)); // Light gray
        } else {
            cell.setBackgroundColor(BaseColor.WHITE);
        }
        return cell;
    }
}
