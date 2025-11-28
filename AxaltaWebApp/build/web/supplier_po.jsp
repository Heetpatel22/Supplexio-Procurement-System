<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ page import="java.util.List" %>
<%@ page import="java.text.SimpleDateFormat" %>
<%
    List poList = (List) request.getAttribute("poList");
    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm");
%>
<!DOCTYPE html>
<html>
<head>
    <title>My Purchase Orders</title>
    <link rel="stylesheet" href="assets/css/common.css">
    <link rel="stylesheet" href="https://stackpath.bootstrapcdn.com/bootstrap/4.5.2/css/bootstrap.min.css">
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/5.15.1/css/all.min.css">
    <style>
        .main-content { margin-left: 250px; padding: 2rem; }
        @media (max-width: 991px) {
            .sidebar { display: none; }
            .main-content { margin-left: 0; }
        }
    </style>
</head>
<body>
<!-- <jsp:include page="WEB-INF/views/includes/header.jsp" /> -->
<jsp:include page="WEB-INF/views/common/sidebar.jsp" />
<div class="main-content">
    <h2 class="mb-4">My Purchase Orders</h2>
    <div class="table-responsive">
        <table class="table table-striped table-bordered align-middle">
            <thead class="table-dark">
                <tr>
                    <th>PO ID</th>
                    <th>Auction</th>
                    <th>Company</th>
                    <th>Material</th>
                    <th>Quantity</th>
                    <th>Amount</th>
                    <th>Status</th>
                    <th>Created At</th>
                    <th>Buyer ID</th>
                    <th>Delivery Address</th>
                    <th>Payment Terms</th>
                    <th>Description</th>
                    <th>Expected Delivery Date</th>
                    <th>Incoterm</th>
                    <th>Incoterm Location</th>
                    <th>Action</th>
                </tr>
            </thead>
            <tbody>
            <% if (poList != null && !poList.isEmpty()) {
                for (Object obj : poList) {
                    Class<?> beanClass = obj.getClass();
            %>
                <tr>
                    <td><%= beanClass.getMethod("getId").invoke(obj) %></td>
                    <td><%= beanClass.getMethod("getAuctionId").invoke(obj) %></td>
                    <td><%= beanClass.getMethod("getCompanyName").invoke(obj) %></td>
                    <td><%= beanClass.getMethod("getMaterial").invoke(obj) %></td>
                    <td><%= beanClass.getMethod("getQuantity").invoke(obj) %></td>
                    <td>$<%= beanClass.getMethod("getAmount").invoke(obj) %></td>
                    <td><span class="badge bg-info"><%= beanClass.getMethod("getStatus").invoke(obj) %></span></td>
                    <td><%= sdf.format(beanClass.getMethod("getCreatedAt").invoke(obj)) %></td>
                    <td><%= beanClass.getMethod("getBuyerId").invoke(obj) %></td>
                    <td><%= beanClass.getMethod("getDeliveryAddress").invoke(obj) %></td>
                    <td><%= beanClass.getMethod("getPaymentTerms").invoke(obj) %></td>
                    <td><%= beanClass.getMethod("getDescription").invoke(obj) %></td>
                    <td><%= sdf.format(beanClass.getMethod("getExpectedDeliveryDate").invoke(obj)) %></td>
                    <td><%= beanClass.getMethod("getIncoterm").invoke(obj) %></td>
                    <td><%= beanClass.getMethod("getIncotermLocation").invoke(obj) %></td>
                    <td>
                        <form method="get" action="download-po-pdf" style="display:inline;">
                            <input type="hidden" name="poId" value="<%= beanClass.getMethod("getId").invoke(obj) %>" />
                            <button type="submit" class="btn btn-sm btn-success">Download as PDF</button>
                        </form>
                    </td>
                </tr>
            <% } } else { %>
                <tr><td colspan="15" class="text-center">No purchase orders found.</td></tr>
            <% } %>
            </tbody>
        </table>
    </div>
</div>
<jsp:include page="WEB-INF/views/includes/footer.jsp" />
</body>
</html>
