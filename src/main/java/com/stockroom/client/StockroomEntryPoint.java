package com.stockroom.client;

import com.google.gwt.core.client.EntryPoint;
import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.http.client.Request;
import com.google.gwt.http.client.RequestBuilder;
import com.google.gwt.http.client.RequestCallback;
import com.google.gwt.http.client.RequestException;
import com.google.gwt.http.client.Response;
import com.google.gwt.i18n.client.DateTimeFormat;
import com.google.gwt.json.client.JSONArray;
import com.google.gwt.json.client.JSONNumber;
import com.google.gwt.json.client.JSONObject;
import com.google.gwt.json.client.JSONParser;
import com.google.gwt.json.client.JSONString;
import com.google.gwt.json.client.JSONValue;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.CheckBox;
import com.google.gwt.user.client.ui.DialogBox;
import com.google.gwt.user.client.ui.FileUpload;
import com.google.gwt.user.client.ui.FlexTable;
import com.google.gwt.user.client.ui.FormPanel;
import com.google.gwt.user.client.ui.HasHorizontalAlignment;
import com.google.gwt.user.client.ui.HorizontalPanel;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.ListBox;
import com.google.gwt.user.client.ui.PasswordTextBox;
import com.google.gwt.user.client.ui.RootPanel;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.VerticalPanel;
import com.google.gwt.user.client.ui.Widget;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public final class StockroomEntryPoint implements EntryPoint {
    private final RootPanel root = RootPanel.get();
    private final List<Product> products = new ArrayList<Product>();
    private JSONObject user;
    private JSONObject store;
    private String currentView = "Overview";
    private String activeTab = "All";
    private int threshold = 15;
    private Label notice;
    private TextBox search;
    private String searchQuery = "";

    @Override public void onModuleLoad() {
        root.clear();
        root.getElement().getStyle().setBackgroundColor("#f4f7f4");
        root.getElement().getStyle().setProperty("fontFamily", "Inter, Segoe UI, Arial, sans-serif");
        root.getElement().getStyle().setProperty("minHeight", "100vh");
        request("GET", "/api/auth/me", null, new ApiCallback() {
            @Override public void success(JSONValue data) { setAccount(data.isObject()); loadDashboard(); }
            @Override public void failure(String message) { showAuth(false, ""); }
        });
    }

    private void setAccount(JSONObject account) {
        user = account.get("user").isObject();
        store = account.get("store").isObject();
    }

    private void showAuth(boolean signup, String error) {
        root.clear();
        VerticalPanel page = new VerticalPanel(); page.setWidth("100%"); page.setHorizontalAlignment(HasHorizontalAlignment.ALIGN_CENTER);
        page.getElement().getStyle().setProperty("paddingTop", "9vh");
        VerticalPanel card = new VerticalPanel(); card.setSpacing(10); card.setWidth("380px");
        card.getElement().getStyle().setBackgroundColor("#ffffff");
        card.getElement().getStyle().setPadding(30, Unit.PX);
        card.getElement().getStyle().setProperty("borderRadius", "18px");
        card.getElement().getStyle().setProperty("boxShadow", "0 18px 48px rgba(24, 51, 37, .10)");
        card.add(styledLabel("⌂  stockroom", "#216746", 22, true));
        card.add(styledLabel(signup ? "Set up your shop" : "Welcome back", "#17251d", 27, true));
        card.add(new Label(signup ? "Create an account for your store." : "Sign in to see your inventory."));
        if (!error.isEmpty()) card.add(new Label(error));
        if (signup) {
            TextBox name = field("Your name", card);
            TextBox email = field("Email address", card);
            PasswordTextBox password = new PasswordTextBox(); password.getElement().setAttribute("placeholder", "Password (8 characters minimum)"); card.add(password);
            TextBox shop = field("Shop name", card);
            TextBox location = field("Location (optional)", card);
            Button create = new Button("Create my shop account"); create.addClickHandler(e -> {
                JSONObject body = new JSONObject(); put(body,"name",name.getValue()); put(body,"email",email.getValue()); put(body,"password",password.getValue()); put(body,"storeName",shop.getValue()); put(body,"storeLocation",location.getValue());
                request("POST","/api/auth/signup",body.toString(),new ApiCallback(){public void success(JSONValue v){setAccount(v.isObject());loadDashboard();}public void failure(String m){showAuth(true,m);}});
            }); card.add(create);
        } else {
            TextBox email = field("Email address", card);
            PasswordTextBox password = new PasswordTextBox(); password.getElement().setAttribute("placeholder", "Password"); card.add(password);
            Button login = new Button("Log in to your store"); login.addClickHandler(e -> {
                JSONObject body=new JSONObject();put(body,"email",email.getValue());put(body,"password",password.getValue());
                request("POST","/api/auth/login",body.toString(),new ApiCallback(){public void success(JSONValue v){setAccount(v.isObject());loadDashboard();}public void failure(String m){showAuth(false,m);}});
            }); card.add(login);
        }
        Button switchMode = new Button(signup ? "Already have an account? Log in" : "Create a shopkeeper account");
        switchMode.addClickHandler(e -> showAuth(!signup,"")); card.add(switchMode);
        card.add(new Label("Each shop has its own private inventory, suppliers and settings."));
        page.add(card); root.add(page);
    }

    private TextBox field(String placeholder, VerticalPanel panel) { TextBox box=new TextBox();box.setWidth("95%");box.getElement().setAttribute("placeholder",placeholder);panel.add(box);return box; }

    private Label styledLabel(String text,String color,int size,boolean bold){Label label=new Label(text);label.getElement().getStyle().setColor(color);label.getElement().getStyle().setFontSize(size,Unit.PX);if(bold)label.getElement().getStyle().setProperty("fontWeight","700");return label;}

    private void loadDashboard() {
        root.clear();
        VerticalPanel app=new VerticalPanel();app.setWidth("100%");app.setSpacing(8);
        app.getElement().getStyle().setPadding(22, Unit.PX);
        app.getElement().getStyle().setProperty("boxSizing", "border-box");
        app.getElement().getStyle().setProperty("maxWidth", "1440px");
        app.getElement().getStyle().setProperty("margin", "0 auto");
        HorizontalPanel header=new HorizontalPanel();header.setWidth("100%");header.setSpacing(8);
        header.getElement().getStyle().setPadding(14, Unit.PX);
        header.getElement().getStyle().setBackgroundColor("#ffffff");
        header.getElement().getStyle().setProperty("borderRadius", "14px");
        header.getElement().getStyle().setProperty("boxShadow", "0 4px 18px rgba(24, 51, 37, .06)");
        header.add(styledLabel("⌂  stockroom", "#216746", 21, true));header.add(new Label(text(store,"name","Your shop")));
        Button storeButton=new Button("Store details");storeButton.addClickHandler(e->showStoreDialog());header.add(storeButton);
        Button profileButton=new Button(text(user,"name","Shopkeeper"));profileButton.addClickHandler(e->showProfileDialog());header.add(profileButton);
        Button logout=new Button("Sign out");logout.addClickHandler(e->request("POST","/api/auth/logout","{}",new ApiCallback(){public void success(JSONValue v){user=null;store=null;showAuth(false,"You have signed out.");}public void failure(String m){showAuth(false,"Please sign in again.");}}));header.add(logout);
        app.add(header);
        HorizontalPanel nav=new HorizontalPanel();nav.setSpacing(5);
        String[] views={"Overview","Inventory","Expiry tracker","Suppliers","Sales & reports","Settings","Help & guides"};
        for(final String view:views){Button b=new Button(view);b.getElement().getStyle().setProperty("borderRadius", "9px");b.getElement().getStyle().setPadding(9, Unit.PX);if(view.equals(currentView)){b.getElement().getStyle().setBackgroundColor("#e7f1e9");b.getElement().getStyle().setColor("#216746");}b.addClickHandler(e->{currentView=view;renderContent();});nav.add(b);}
        app.add(nav);notice=new Label("");app.add(notice);
        root.add(app);
        request("GET","/api/settings",null,new ApiCallback(){public void success(JSONValue v){threshold=Math.max(1,Math.min(500,(int)number(v.isObject(),"lowStockThreshold")));loadProductsThen(StockroomEntryPoint.this::renderContent);}public void failure(String m){toast(m);loadProductsThen(StockroomEntryPoint.this::renderContent);}});
    }

    private void renderContent() {
        if (root.getWidgetCount()==0) return;
        VerticalPanel app=(VerticalPanel)root.getWidget(0);
        while(app.getWidgetCount()>3) app.remove(app.getWidgetCount()-1);
        HorizontalPanel title=new HorizontalPanel();title.setSpacing(8);title.add(new Label("Good day, "+first(text(user,"name","Shopkeeper"))));title.add(new Label(text(store,"name","Your shop")));app.add(title);
        if(currentView.equals("Overview")){showOverview(app);return;}
        if(currentView.equals("Inventory")){showInventory(app);return;}
        if(currentView.equals("Expiry tracker")){showExpiry(app);return;}
        if(currentView.equals("Suppliers")){showSuppliers(app);return;}
        if(currentView.equals("Sales & reports")){showReports(app);return;}
        if(currentView.equals("Settings")){showSettings(app);return;}
        showHelp(app);
    }

    private void showOverview(VerticalPanel app) {
        int low=0,soon=0,value=0;for(Product p:products){if(p.stock<threshold)low++;if(days(p.expiry)<=7)soon++;value+=(int)(p.price*p.stock);}
        HorizontalPanel stats=new HorizontalPanel();stats.setSpacing(12);stats.add(new Label("Products: "+products.size()));stats.add(new Label("Low stock: "+low));stats.add(new Label("Expiring soon: "+soon));stats.add(new Label("Stock value: ₹"+value));app.add(stats);
        app.add(new Label("Expiry tracker"));FlexTable expiry=productTable(filter("Expiring"),false);app.add(expiry);
        app.add(new Label("Inventory"));app.add(productTable(products,false));
    }

    private void showInventory(VerticalPanel app) {
        HorizontalPanel controls=new HorizontalPanel();controls.setSpacing(8);
        search=new TextBox();search.setValue(searchQuery);search.getElement().setAttribute("placeholder","Search by product or SKU");Button find=new Button("Search");find.addClickHandler(e->{searchQuery=search.getValue().trim();renderContent();});controls.add(search);controls.add(find);
        Button add=new Button("Add product");add.addClickHandler(e->showProductDialog());controls.add(add);
        app.add(controls);
        HorizontalPanel tabs=new HorizontalPanel();tabs.setSpacing(5);for(final String t:new String[]{"All","Low stock","Expiring"}){Button b=new Button(t);b.addClickHandler(e->{activeTab=t;renderContent();});tabs.add(b);}app.add(tabs);
        app.add(productTable(filter(activeTab),true));
    }

    private void showExpiry(VerticalPanel app){app.add(new Label("Products expiring within 7 days"));app.add(productTable(filter("Expiring"),true));}

    private FlexTable productTable(List<Product> list,boolean actions) {
        FlexTable table=new FlexTable();table.setCellPadding(5);
        String[] headers={"Product","SKU","Category","Price","Stock","Expiry","Status"};for(int i=0;i<headers.length;i++)table.setText(0,i,headers[i]);
        for(int r=0;r<list.size();r++){final Product p=list.get(r);int row=r+1;table.setText(row,0,p.name);table.setText(row,1,p.sku);table.setText(row,2,p.category);table.setText(row,3,"₹"+p.price);table.setText(row,4,p.stock+" units");table.setText(row,5,p.expiry.isEmpty()?"—":p.expiry);table.setText(row,6,status(p));
            if(actions){HorizontalPanel buttons=new HorizontalPanel();buttons.setSpacing(3);Button minus=new Button("−");minus.addClickHandler(e->adjust(p,-1));Button plus=new Button("+");plus.addClickHandler(e->adjust(p,1));Button del=new Button("Remove");del.addClickHandler(e->deleteProduct(p));buttons.add(minus);buttons.add(plus);buttons.add(del);table.setWidget(row,7,buttons);}}
        if(list.isEmpty())table.setText(1,0,"No products to display yet.");return table;
    }

    private void showProductDialog() {
        final DialogBox dialog=new DialogBox();dialog.setText("Add a product");VerticalPanel form=new VerticalPanel();form.setSpacing(7);
        final TextBox name=field("Product name",form);final ListBox category=new ListBox();for(String c:new String[]{"Dairy","Produce","Bakery","Pantry","Other"})category.addItem(c);form.add(category);
        final TextBox price=field("Price (₹)",form),stock=field("Quantity in stock",form),expiry=field("Expiry date (YYYY-MM-DD)",form);
        FormPanel scan=new FormPanel();scan.setAction("/api/ocr/expiry");scan.setMethod(FormPanel.METHOD_POST);scan.setEncoding(FormPanel.ENCODING_MULTIPART);
        VerticalPanel scanPanel=new VerticalPanel();scanPanel.setSpacing(5);scanPanel.add(new Label("Scan an expiry date from a label photo"));FileUpload image=new FileUpload();image.setName("image");scanPanel.add(image);Button scanButton=new Button("Scan label");scanButton.addClickHandler(e->{if(image.getFilename().isEmpty()){toast("Choose a label photo first.");return;}scan.submit();});scanPanel.add(scanButton);scan.setWidget(scanPanel);
        scan.addSubmitCompleteHandler(e->{String result=e.getResults();String detected=extractDate(result);if(!detected.isEmpty()){expiry.setValue(detected);toast("Detected expiry date: "+detected+" — please review it.");}else toast("No clear date found. Enter the expiry date manually.");});form.add(scan);
        HorizontalPanel buttons=new HorizontalPanel();buttons.setSpacing(6);Button save=new Button("Save product");save.addClickHandler(e->{try{JSONObject body=new JSONObject();put(body,"name",name.getValue());put(body,"category",category.getSelectedValue());body.put("price",new JSONNumber(Double.parseDouble(price.getValue())));body.put("stock",new JSONNumber(Double.parseDouble(stock.getValue())));put(body,"icon","📦");if(!expiry.getValue().trim().isEmpty())put(body,"expiry",expiry.getValue().trim());request("POST","/api/products",body.toString(),new ApiCallback(){public void success(JSONValue v){dialog.hide();loadProductsThen(StockroomEntryPoint.this::renderContent);toast("Product added.");}public void failure(String m){toast(m);}});}catch(Exception ex){toast("Enter a valid price and quantity.");}});
        Button cancel=new Button("Cancel");cancel.addClickHandler(e->dialog.hide());buttons.add(save);buttons.add(cancel);form.add(buttons);dialog.setWidget(form);dialog.center();
    }

    private void showSuppliers(VerticalPanel app) {
        TextBox name=field("Supplier name",app),category=field("Category",app),phone=field("Phone (optional)",app),email=field("Email (optional)",app);
        Button add=new Button("Add supplier");add.addClickHandler(e->{JSONObject body=new JSONObject();put(body,"name",name.getValue());put(body,"category",category.getValue());put(body,"phone",phone.getValue());put(body,"email",email.getValue());request("POST","/api/suppliers",body.toString(),new ApiCallback(){public void success(JSONValue v){toast("Supplier added.");renderSuppliers(app);}public void failure(String m){toast(m);}});});app.add(add);renderSuppliers(app);
    }
    private void renderSuppliers(final VerticalPanel app){request("GET","/api/suppliers",null,new ApiCallback(){public void success(JSONValue v){if(v.isArray()==null)return;JSONArray rows=v.isArray();FlexTable t=new FlexTable();t.setCellPadding(5);t.setText(0,0,"Supplier");t.setText(0,1,"Category");t.setText(0,2,"Contact");for(int i=0;i<rows.size();i++){JSONObject s=rows.get(i).isObject();int row=i+1;t.setText(row,0,text(s,"name",""));t.setText(row,1,text(s,"category",""));t.setText(row,2,text(s,"phone",text(s,"email","")));final String id=String.valueOf((long)number(s,"id"));Button remove=new Button("Remove");remove.addClickHandler(e->request("DELETE","/api/suppliers/"+id,null,new ApiCallback(){public void success(JSONValue x){renderContent();}public void failure(String m){toast(m);}}));t.setWidget(row,3,remove);}app.add(t);}public void failure(String m){toast(m);}});}

    private void showReports(final VerticalPanel app){
        int value=0,units=0,low=0,soon=0;for(Product p:products){value+=(int)(p.price*p.stock);units+=p.stock;if(p.stock<threshold)low++;if(days(p.expiry)<=7)soon++;}
        app.add(styledLabel("Sales & reports", "#17251d", 24, true));
        app.add(new Label("Inventory value: ₹"+value+"  •  Units on hand: "+units+"  •  Low stock: "+low+"  •  Expiring within 7 days: "+soon));
        app.add(new Label("Record a sale"));
        final ListBox product=new ListBox();for(Product p:products)if(p.stock>0)product.addItem(p.name+" ("+p.stock+" available)",String.valueOf(p.id));
        final TextBox quantity=new TextBox();quantity.setValue("1");quantity.getElement().setAttribute("placeholder","Units sold");
        Button record=new Button("Record sale and update stock");record.addClickHandler(e->{try{if(product.getItemCount()==0){toast("Add stock before recording a sale.");return;}JSONObject body=new JSONObject();body.put("productId",new JSONNumber(Double.parseDouble(product.getSelectedValue())));body.put("units",new JSONNumber(Integer.parseInt(quantity.getValue())));request("POST","/api/sales",body.toString(),new ApiCallback(){public void success(JSONValue v){toast("Sale recorded and stock updated.");loadProductsThen(StockroomEntryPoint.this::renderContent);}public void failure(String m){toast(m);}});}catch(Exception ex){toast("Choose a product and enter a whole-number quantity.");}});
        HorizontalPanel controls=new HorizontalPanel();controls.setSpacing(8);controls.add(product);controls.add(quantity);controls.add(record);app.add(controls);
        request("GET","/api/sales",null,new ApiCallback(){public void success(JSONValue data){JSONObject summary=data.isObject();app.add(new Label("Recorded sales: "+number(summary,"count")+"  •  Units sold: "+number(summary,"units")+"  •  Revenue: ₹"+number(summary,"revenue")));app.add(new Label("Recent sales"));JSONArray rows=summary.get("recent").isArray();FlexTable table=new FlexTable();table.setCellPadding(5);table.setText(0,0,"Product");table.setText(0,1,"Units");table.setText(0,2,"Unit price");table.setText(0,3,"Total");table.setText(0,4,"Date");for(int i=0;i<rows.size();i++){JSONObject s=rows.get(i).isObject();int row=i+1;table.setText(row,0,text(s,"product",""));table.setText(row,1,String.valueOf((int)number(s,"units")));table.setText(row,2,"₹"+number(s,"unitPrice"));table.setText(row,3,"₹"+number(s,"amount"));table.setText(row,4,text(s,"soldAt",""));}if(rows.size()==0)table.setText(1,0,"No sales recorded yet.");app.add(table);}public void failure(String m){toast(m);}});
    }

    private void showSettings(VerticalPanel app){app.add(new Label("Low stock alert threshold"));final TextBox input=new TextBox();input.setValue(String.valueOf(threshold));app.add(input);Button save=new Button("Save settings");save.addClickHandler(e->{try{JSONObject body=new JSONObject();body.put("lowStockThreshold",new JSONNumber(Integer.parseInt(input.getValue())));request("PUT","/api/settings",body.toString(),new ApiCallback(){public void success(JSONValue v){threshold=(int)number(v.isObject(),"lowStockThreshold");toast("Settings saved.");renderContent();}public void failure(String m){toast(m);}});}catch(Exception ex){toast("Enter a whole number between 1 and 500.");}});app.add(save);}

    private void showHelp(VerticalPanel app){app.add(new Label("Add products from Inventory, then enter the price, quantity and expiry date."));app.add(new Label("Use Scan label to detect an expiry date from the package photo. Review the date before saving."));app.add(new Label("Use the plus and minus buttons to adjust stock; remove deletes a product."));app.add(new Label("Each account has separate products, suppliers, preferences and store details."));}

    private void showProfileDialog(){final DialogBox d=new DialogBox();d.setText("Owner profile");VerticalPanel p=new VerticalPanel();p.setSpacing(7);final TextBox name=field("Full name",p);name.setValue(text(user,"name",""));final TextBox email=field("Email",p);email.setValue(text(user,"email",""));HorizontalPanel buttons=new HorizontalPanel();Button save=new Button("Save profile");save.addClickHandler(e->{JSONObject body=new JSONObject();put(body,"name",name.getValue());put(body,"email",email.getValue());request("PUT","/api/auth/profile",body.toString(),new ApiCallback(){public void success(JSONValue v){setAccount(v.isObject());d.hide();loadDashboard();toast("Profile saved.");}public void failure(String m){toast(m);}});});Button close=new Button("Close");close.addClickHandler(e->d.hide());buttons.add(save);buttons.add(close);p.add(buttons);d.setWidget(p);d.center();}
    private void showStoreDialog(){final DialogBox d=new DialogBox();d.setText("Store details");VerticalPanel p=new VerticalPanel();p.setSpacing(7);final TextBox name=field("Store name",p);name.setValue(text(store,"name",""));final TextBox location=field("Location",p);location.setValue(text(store,"location",""));Button save=new Button("Save store");save.addClickHandler(e->{JSONObject body=new JSONObject();put(body,"name",name.getValue());put(body,"location",location.getValue());request("PUT","/api/store",body.toString(),new ApiCallback(){public void success(JSONValue v){setAccount(v.isObject());d.hide();loadDashboard();toast("Store details saved.");}public void failure(String m){toast(m);}});});p.add(save);d.setWidget(p);d.center();}

    private List<Product> filter(String type){List<Product> out=new ArrayList<Product>();String q=currentView.equals("Inventory")?searchQuery.toLowerCase():"";for(Product p:products){boolean match=q.isEmpty()||p.name.toLowerCase().contains(q)||p.sku.toLowerCase().contains(q);if(!match)continue;if(type.equals("Low stock")&&p.stock>=threshold)continue;if(type.equals("Expiring")&&days(p.expiry)>7)continue;out.add(p);}return out;}
    private void adjust(final Product p,int delta){JSONObject body=productJson(p);body.put("stock",new JSONNumber(Math.max(0,p.stock+delta)));request("PUT","/api/products/"+p.id,body.toString(),new ApiCallback(){public void success(JSONValue v){loadProductsThen(StockroomEntryPoint.this::renderContent);}public void failure(String m){toast(m);}});}
    private void deleteProduct(final Product p){if(!Window.confirm("Remove "+p.name+" from inventory?"))return;request("DELETE","/api/products/"+p.id,null,new ApiCallback(){public void success(JSONValue v){loadProductsThen(StockroomEntryPoint.this::renderContent);toast("Product removed.");}public void failure(String m){toast(m);}});}
    private JSONObject productJson(Product p){JSONObject b=new JSONObject();put(b,"name",p.name);put(b,"category",p.category);b.put("price",new JSONNumber(p.price));b.put("stock",new JSONNumber(p.stock));put(b,"expiry",p.expiry);put(b,"icon",p.icon);return b;}
    private void loadProductsThen(final Runnable done){request("GET","/api/products",null,new ApiCallback(){public void success(JSONValue v){products.clear();JSONArray a=v.isArray();if(a!=null)for(int i=0;i<a.size();i++){JSONObject o=a.get(i).isObject();products.add(new Product(o));}done.run();}public void failure(String m){toast(m);done.run();}});}
    private void loadDashboardProducts(){loadProductsThen(this::renderContent);}
    private void loadProducts(){loadProductsThen(this::renderContent);}

    private void request(String method,String url,String body,final ApiCallback callback){try{RequestBuilder b=new RequestBuilder(requestMethod(method),url);b.setHeader("Accept","application/json");if(body!=null){b.setHeader("Content-Type","application/json");}b.sendRequest(body,new RequestCallback(){public void onResponseReceived(Request req,Response res){try{JSONValue v=JSONParser.parseStrict(res.getText());if(res.getStatusCode()>=200&&res.getStatusCode()<300)callback.success(v);else callback.failure(error(v,res.getStatusCode()));}catch(Exception e){if(res.getStatusCode()>=200&&res.getStatusCode()<300)callback.success(null);else callback.failure("Request failed ("+res.getStatusCode()+")");}}public void onError(Request req,Throwable e){callback.failure("Could not connect to Stockroom. Check your connection and try again.");}});}catch(RequestException e){callback.failure("Could not send the request.");}}
    private void toast(String message){if(notice!=null)notice.setText(message);else Window.alert(message);}
    private int days(String expiry){if(expiry==null||expiry.isEmpty())return 999;try{DateTimeFormat f=DateTimeFormat.getFormat("yyyy-MM-dd");Date a=f.parseStrict(expiry),b=f.parseStrict(f.format(new Date()));return (int)((a.getTime()-b.getTime())/86400000L);}catch(Exception e){return 999;}}
    private String status(Product p){if(p.stock==0)return "Out of stock";if(p.stock<threshold)return "Low stock";if(days(p.expiry)<=7)return "Expiring";return "In stock";}
    private String extractDate(String text){if(text==null)return "";for(int i=0;i+10<=text.length();i++){String x=text.substring(i,i+10);if(x.charAt(0)=='2'&&x.charAt(1)=='0'&&x.charAt(4)=='-'&&x.charAt(7)=='-'&&digits(x,0,4)&&digits(x,5,7)&&digits(x,8,10))return x;}return "";}
    private boolean digits(String x,int from,int to){for(int i=from;i<to;i++)if(x.charAt(i)<'0'||x.charAt(i)>'9')return false;return true;}
    private RequestBuilder.Method requestMethod(String name){if(name.equals("POST"))return RequestBuilder.POST;if(name.equals("PUT"))return RequestBuilder.PUT;if(name.equals("DELETE"))return RequestBuilder.DELETE;return RequestBuilder.GET;}
    private static void put(JSONObject o,String key,String value){o.put(key,new JSONString(value==null?"":value));}
    private static String text(JSONObject o,String key,String fallback){if(o==null||o.get(key)==null||o.get(key).isString()==null)return fallback;return o.get(key).isString().stringValue();}
    private static double number(JSONObject o,String key){if(o==null||o.get(key)==null)return 0;try{if(o.get(key).isNumber()!=null)return o.get(key).isNumber().doubleValue();if(o.get(key).isString()!=null)return Double.parseDouble(o.get(key).isString().stringValue());}catch(Exception ignored){}return 0;}
    private static String first(String name){int i=name.indexOf(' ');return i<0?name:name.substring(0,i);}
    private static String error(JSONValue v,int code){if(v!=null&&v.isObject()!=null){String e=text(v.isObject(),"error","");if(!e.isEmpty())return e;}return "Request failed ("+code+")";}

    private interface ApiCallback { void success(JSONValue data); void failure(String message); }
    private static final class Product {final long id;final String name,sku,category,expiry,icon;final int stock;final double price;Product(JSONObject o){id=(long)number(o,"id");name=text(o,"name","");sku=text(o,"sku","");category=text(o,"category","");expiry=text(o,"expiry","");icon=text(o,"icon","📦");stock=(int)number(o,"stock");price=number(o,"price");}}
}
