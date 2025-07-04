package com.group7.pawdicted;

import android.content.Intent;
import android.graphics.Paint;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.group7.pawdicted.mobile.adapters.ChildCategoryAdapter;
import com.group7.pawdicted.mobile.adapters.ProductFilteredAdapter;
import com.group7.pawdicted.mobile.models.Category;
import com.group7.pawdicted.mobile.models.ChildCategory;
import com.group7.pawdicted.mobile.models.ListCategory;
import com.group7.pawdicted.mobile.models.ListChildCategory;
import com.group7.pawdicted.mobile.models.Product;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CategoryDetailsActivity extends AppCompatActivity {

    private RecyclerView childCategoryRecyclerView;
    private LinearLayout filterCategoryLayout, productContainer;
    private TextView bestSellerText, newestText, ratingText, priceText;
    private ImageView priceArrow;
    private LinearLayout bestSellerLayout, newestLayout, ratingLayout, priceLayout;
    private ChildCategoryAdapter childCategoryAdapter;
    private ProductFilteredAdapter productAdapter;
    private ListChildCategory listChildCategory;
    private ListCategory listCategory;
    private FirebaseFirestore db;
    private String categoryId;
    private String childCategoryId;
    private int animalClassId = -2; // -2: none, -1: all, 0: cat, 1: dog
    private List<Product> filteredProducts;
    private boolean isPriceAscending = true;
    private String selectedFilter = "none";

    // THÊM CÁC BIẾN FLASHSALE
    private Map<String, Integer> productFlashsaleDiscounts = new HashMap<>();
    private Map<String, String> productFlashsaleIds = new HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_category_details);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.LinearLayout), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        db = FirebaseFirestore.getInstance();

        ImageView imgBack = findViewById(R.id.imgBack);
        if (imgBack != null) {
            imgBack.setOnClickListener(v -> finish());
        }

        Intent intent = getIntent();
        categoryId = intent.getStringExtra("category_id");
        childCategoryId = intent.getStringExtra("child_category_id");
        animalClassId = intent.getIntExtra("animal_class_id", -2);
        Log.d("CategoryDetailsActivity", "Received: category_id=" + categoryId + ", child_category_id=" + childCategoryId + ", animal_class_id=" + animalClassId);

        listCategory = new ListCategory();
        try {
            listCategory.generate_sample_dataset();
        } catch (Exception e) {
            Log.e("CategoryDetailsActivity", "Error generating sample category data", e);
        }

        listChildCategory = new ListChildCategory();
        try {
            listChildCategory.generate_sample_dataset();
        } catch (Exception e) {
            Log.e("CategoryDetailsActivity", "Error generating sample child category data", e);
        }

        TextView txtPageTitle = findViewById(R.id.txt_page_title);
        String displayName = getDisplayName(categoryId, childCategoryId);
        if (displayName != null && txtPageTitle != null) {
            txtPageTitle.setText(displayName);
        } else {
            txtPageTitle.setText("Category");
        }

        childCategoryRecyclerView = findViewById(R.id.child_category_container);
        filterCategoryLayout = findViewById(R.id.filter_category);
        productContainer = findViewById(R.id.product_filtered_category);
        bestSellerLayout = findViewById(R.id.layout_best_seller);
        newestLayout = findViewById(R.id.layout_newest);
        ratingLayout = findViewById(R.id.layout_rating);
        priceLayout = findViewById(R.id.layout_price);
        bestSellerText = findViewById(R.id.child_cate_best_seller);
        newestText = findViewById(R.id.child_cate_newest);
        ratingText = findViewById(R.id.child_care_rating);
        priceText = findViewById(R.id.child_cate_price);
        priceArrow = findViewById(R.id.img_ascend_arrows);

        childCategoryRecyclerView.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        updateChildCategories();

        filteredProducts = new ArrayList<>();
        productAdapter = new ProductFilteredAdapter(this, filteredProducts);
        updateFilterUI();
        fetchProductsFromFirestore();

        bestSellerLayout.setOnClickListener(v -> {
            if (!selectedFilter.equals("best_seller")) {
                selectedFilter = "best_seller";
                updateFilterUI();
                sortProductsByBestSeller();
                updateProductContainer();
            } else {
                selectedFilter = "none";
                updateFilterUI();
                fetchProductsFromFirestore();
            }
        });

        newestLayout.setOnClickListener(v -> {
            if (!selectedFilter.equals("newest")) {
                selectedFilter = "newest";
                updateFilterUI();
                sortProductsByNewest();
                updateProductContainer();
            } else {
                selectedFilter = "none";
                updateFilterUI();
                fetchProductsFromFirestore();
            }
        });

        ratingLayout.setOnClickListener(v -> {
            if (!selectedFilter.equals("rating")) {
                selectedFilter = "rating";
                updateFilterUI();
                sortProductsByRating();
                updateProductContainer();
            } else {
                selectedFilter = "none";
                updateFilterUI();
                fetchProductsFromFirestore();
            }
        });

        priceLayout.setOnClickListener(v -> {
            if (!selectedFilter.equals("price")) {
                selectedFilter = "price";
                isPriceAscending = true;
            } else {
                isPriceAscending = !isPriceAscending;
            }
            updateFilterUI();
            sortProductsByPrice();
            updateProductContainer();
        });
    }

    private String getDisplayName(String categoryId, String childCategoryId) {
        if (childCategoryId != null) {
            for (ChildCategory childCategory : listChildCategory.getChildCategories()) {
                if (childCategory.getChildCategory_id().equals(childCategoryId)) {
                    return childCategory.getChildCategory_name();
                }
            }
        }
        if (categoryId != null) {
            for (Category category : listCategory.getCategories()) {
                if (category.getCategory_id().equals(categoryId)) {
                    return category.getCategory_name();
                }
            }
        }
        return null;
    }

    private void updateChildCategories() {
        List<ChildCategory> relatedChildCategories = getRelatedChildCategories();
        childCategoryAdapter = new ChildCategoryAdapter(this, relatedChildCategories, childCategoryId -> {
            this.childCategoryId = childCategoryId;
            fetchProductsFromFirestore();
            TextView txtPageTitle = findViewById(R.id.txt_page_title);
            String displayName = getDisplayName(categoryId, childCategoryId);
            if (displayName != null && txtPageTitle != null) {
                txtPageTitle.setText(displayName);
            }
            updateChildCategories();
        });
        childCategoryRecyclerView.setAdapter(childCategoryAdapter);
    }

    private List<ChildCategory> getRelatedChildCategories() {
        List<ChildCategory> childCategories = new ArrayList<>();
        List<ChildCategory> allChildCategories = listChildCategory.getChildCategories();
        if (allChildCategories != null) {
            for (ChildCategory childCategory : allChildCategories) {
                if (childCategory.getCategory_id().equals(categoryId) &&
                        !childCategory.getChildCategory_id().equals(childCategoryId)) {
                    childCategories.add(childCategory);
                }
            }
        }
        return childCategories;
    }

    private void fetchProductsFromFirestore() {
        if (childCategoryId == null) {
            Log.e("CategoryDetailsActivity", "childCategoryId is null");
            filteredProducts.clear();
            updateProductContainer();
            return;
        }

        db.collection("products")
                .whereEqualTo("child_category_id", childCategoryId)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    filteredProducts.clear();
                    for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                        Product product = document.toObject(Product.class);
                        if (animalClassId == -2 || animalClassId == -1 || product.getAnimal_class_id() == animalClassId) {
                            filteredProducts.add(product);
                        }
                    }
                    Log.d("CategoryDetailsActivity", "Products found: " + filteredProducts.size());

                    // CHECK FLASHSALE AFTER LOADING PRODUCTS
                    checkProductsInFlashsale();

                    switch (selectedFilter) {
                        case "best_seller":
                            sortProductsByBestSeller();
                            break;
                        case "newest":
                            sortProductsByNewest();
                            break;
                        case "rating":
                            sortProductsByRating();
                            break;
                        case "price":
                            sortProductsByPrice();
                            break;
                    }
                    productAdapter.notifyDataSetChanged();
                    // updateProductContainer() sẽ được gọi trong checkProductsInFlashsale()
                })
                .addOnFailureListener(e -> {
                    Log.e("CategoryDetailsActivity", "Error fetching products: " + e.getMessage());
                    filteredProducts.clear();
                    updateProductContainer();
                });
    }

    // THÊM METHOD CHECK FLASHSALE
    private void checkProductsInFlashsale() {
        Log.d("CategoryDetailsActivity", "Checking products in flashsale...");

        // Clear previous flashsale data
        productFlashsaleDiscounts.clear();
        productFlashsaleIds.clear();

        // Get all product IDs from filtered products
        List<String> productIds = new ArrayList<>();
        for (Product product : filteredProducts) {
            productIds.add(product.getProduct_id());
        }

        if (productIds.isEmpty()) {
            Log.d("CategoryDetailsActivity", "No products to check for flashsale");
            updateProductContainer();
            return;
        }

        // Query flashsales collection
        db.collection("flashsales")
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    Log.d("CategoryDetailsActivity", "Found " + querySnapshot.size() + " flashsales");

                    for (QueryDocumentSnapshot flashsaleDoc : querySnapshot) {
                        String flashsaleId = flashsaleDoc.getString("flashSale_id");
                        List<Map<String, Object>> products = (List<Map<String, Object>>) flashsaleDoc.get("products");

                        if (products != null) {
                            for (Map<String, Object> productMap : products) {
                                String productId = (String) productMap.get("product_id");

                                // Check if this product is in our filtered products
                                if (productIds.contains(productId)) {
                                    Object discountRateObj = productMap.get("discountRate");
                                    int discountRate = 0;

                                    if (discountRateObj instanceof Long) {
                                        discountRate = ((Long) discountRateObj).intValue();
                                    } else if (discountRateObj instanceof Integer) {
                                        discountRate = (Integer) discountRateObj;
                                    }

                                    // Store flashsale info for this product
                                    productFlashsaleDiscounts.put(productId, discountRate);
                                    productFlashsaleIds.put(productId, flashsaleId);

                                    Log.d("CategoryDetailsActivity", "Product " + productId +
                                            " is in flashsale " + flashsaleId + " with discount " + discountRate + "%");
                                }
                            }
                        }
                    }

                    // Update UI after checking flashsales
                    updateProductContainer();
                })
                .addOnFailureListener(e -> {
                    Log.e("CategoryDetailsActivity", "Error checking flashsales: " + e.getMessage());
                    // Continue without flashsale data
                    updateProductContainer();
                });
    }

    // THÊM HELPER METHODS
    public boolean isProductInFlashsale(String productId) {
        return productFlashsaleDiscounts.containsKey(productId);
    }

    public int getProductFlashsaleDiscount(String productId) {
        return productFlashsaleDiscounts.getOrDefault(productId, 0);
    }

    public String getProductFlashsaleId(String productId) {
        return productFlashsaleIds.get(productId);
    }

    private void updateFilterUI() {
        bestSellerText.setTextColor(getResources().getColor(R.color.black));
        newestText.setTextColor(getResources().getColor(R.color.black));
        ratingText.setTextColor(getResources().getColor(R.color.black));
        priceText.setTextColor(getResources().getColor(R.color.black));
        priceArrow.setImageResource(R.mipmap.ic_ascend_arrows);

        switch (selectedFilter) {
            case "best_seller":
                bestSellerText.setTextColor(getResources().getColor(R.color.main_color));
                break;
            case "newest":
                newestText.setTextColor(getResources().getColor(R.color.main_color));
                break;
            case "rating":
                ratingText.setTextColor(getResources().getColor(R.color.main_color));
                break;
            case "price":
                priceText.setTextColor(getResources().getColor(R.color.main_color));
                priceArrow.setImageResource(isPriceAscending ? R.mipmap.ic_ascend_arrows_red : R.mipmap.ic_ascend_arrows);
                break;
        }
    }

    private void sortProductsByBestSeller() {
        Collections.sort(filteredProducts, (p1, p2) -> Integer.compare(p2.getSold_quantity(), p1.getSold_quantity()));
    }

    private void sortProductsByNewest() {
        Collections.sort(filteredProducts, (p1, p2) -> p2.getDate_listed().compareTo(p1.getDate_listed()));
    }

    private void sortProductsByRating() {
        Collections.sort(filteredProducts, (p1, p2) -> {
            int ratingComparison = Double.compare(p2.getAverage_rating(), p1.getAverage_rating());
            if (ratingComparison == 0) {
                return Integer.compare(p2.getRating_number(), p1.getRating_number());
            }
            return ratingComparison;
        });
    }

    private void sortProductsByPrice() {
        Collections.sort(filteredProducts, (p1, p2) -> {
            // SỬ DỤNG GIÁ FLASHSALE NẾU CÓ
            double price1 = getEffectivePrice(p1);
            double price2 = getEffectivePrice(p2);
            return isPriceAscending ? Double.compare(price1, price2) : Double.compare(price2, price1);
        });
    }

    // THÊM METHOD TÍNH GIÁ HIỆU QUẢ (FLASHSALE HOẶC BÌNH THƯỜNG)
    private double getEffectivePrice(Product product) {
        if (isProductInFlashsale(product.getProduct_id())) {
            int flashsaleDiscount = getProductFlashsaleDiscount(product.getProduct_id());
            return product.getPrice() * (1 - flashsaleDiscount / 100.0);
        } else {
            return product.getPrice() * (1 - product.getDiscount() / 100.0);
        }
    }

    private void updateProductContainer() {
        productContainer.removeAllViews();
        if (filteredProducts.isEmpty()) {
            TextView emptyView = new TextView(this);
            emptyView.setText("No products available");
            emptyView.setGravity(Gravity.CENTER);
            emptyView.setPadding(16, 16, 16, 16);
            emptyView.setTextColor(getResources().getColor(R.color.black));
            emptyView.setTextSize(16f);
            productContainer.addView(emptyView);
            return;
        }

        LayoutInflater inflater = LayoutInflater.from(this);
        LinearLayout rowLayout = null;

        for (int i = 0; i < filteredProducts.size(); i++) {
            if (i % 2 == 0) {
                rowLayout = new LinearLayout(this);
                LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT);
                rowParams.setMargins(0, 8, 0, 8);
                rowLayout.setLayoutParams(rowParams);
                rowLayout.setOrientation(LinearLayout.HORIZONTAL);
                rowLayout.setWeightSum(2f);
                productContainer.addView(rowLayout);
            }

            // THÊM LOGIC HIỂN THỊ GIÁ FLASHSALE
            View productView = createProductView(filteredProducts.get(i));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            params.setMargins(4, 0, 4, 0);
            productView.setLayoutParams(params);
            rowLayout.addView(productView);
        }
    }

    // THÊM METHOD TẠO PRODUCT VIEW VỚI FLASHSALE
    private View createProductView(Product product) {
        View view = LayoutInflater.from(this).inflate(R.layout.item_product, null);

        // Set product name, image, rating như bình thường
        ImageView productImage = view.findViewById(R.id.img_child_cate_product);
        TextView productName = view.findViewById(R.id.txt_child_cate_product_name);
        TextView rating = view.findViewById(R.id.txt_rating);
        TextView sold = view.findViewById(R.id.txt_child_cate_sold);
        TextView originalPrice = view.findViewById(R.id.txt_child_cate_original_price);
        TextView productPrice = view.findViewById(R.id.txt_child_cate_product_price);
        TextView discountText = view.findViewById(R.id.txt_child_cate_product_discount);

        DecimalFormat formatter = new DecimalFormat("#,###");

        productName.setText(product.getProduct_name());
        rating.setText(String.format("%.1f", product.getAverage_rating()));
        sold.setText(product.getSold_quantity() + " sold");

        // THÊM CODE LOAD IMAGE
        if (productImage != null) {
            Glide.with(this)
                    .load(product.getProduct_image())
                    .placeholder(R.mipmap.ic_logo)
                    .error(R.mipmap.ic_logo)
                    .into(productImage);
        }

        // LOGIC GIÁ FLASHSALE
        if (isProductInFlashsale(product.getProduct_id())) {
            // Có flashsale
            int flashsaleDiscount = getProductFlashsaleDiscount(product.getProduct_id());
            double flashsalePrice = product.getPrice() * (1 - flashsaleDiscount / 100.0);

            // Hiển thị giá flashsale
            productPrice.setText(formatter.format(flashsalePrice) + "đ");
            originalPrice.setText(formatter.format(product.getPrice()) + "đ");
            originalPrice.setPaintFlags(originalPrice.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
            discountText.setText("-" + flashsaleDiscount + "%");
            discountText.setVisibility(View.VISIBLE);

            Log.d("CategoryDetailsActivity", "Flashsale product: " + product.getProduct_name() +
                    " - Original: " + product.getPrice() + " - Flashsale: " + flashsalePrice);
        } else {
            // Không có flashsale - giá bình thường
            double discountPrice = product.getPrice() * (1 - product.getDiscount() / 100.0);

            if (product.getDiscount() > 0) {
                // Có discount bình thường
                productPrice.setText(formatter.format(discountPrice) + "đ");
                originalPrice.setText(formatter.format(product.getPrice()) + "đ");
                originalPrice.setPaintFlags(originalPrice.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
                discountText.setText("-" + product.getDiscount() + "%");
                discountText.setVisibility(View.VISIBLE);
            } else {
                // Không có discount
                productPrice.setText(formatter.format(product.getPrice()) + "đ");
                originalPrice.setText("");
                discountText.setVisibility(View.GONE);
            }
        }

        // Click listener để chuyển sang ProductDetailsActivity
        view.setOnClickListener(v -> {
            Intent intent = new Intent(this, ProductDetailsActivity.class);
            intent.putExtra("product_id", product.getProduct_id());

            // Thêm thông tin flashsale nếu có
            if (isProductInFlashsale(product.getProduct_id())) {
                intent.putExtra("IS_FLASHSALE", true);
                intent.putExtra("FLASHSALE_DISCOUNT_RATE", getProductFlashsaleDiscount(product.getProduct_id()));
                intent.putExtra("FLASHSALE_ID", getProductFlashsaleId(product.getProduct_id()));
                intent.putExtra("FLASHSALE_NAME", "Flash Sale");
                intent.putExtra("FLASHSALE_END_TIME", System.currentTimeMillis() + 3600000); // 1 hour from now
            }

            startActivity(intent);
        });

        return view;
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    public void open_search(View view) {
        Intent intent = new Intent(CategoryDetailsActivity.this, SearchActivity.class);
        startActivity(intent);
    }

    public void open_cart(View view) {
        Intent intent = new Intent(CategoryDetailsActivity.this, CartActivity.class);
        startActivity(intent);
    }
}
