package com.solacesystems.demo;

class Trade {

    public String getInstrument() {
        return _instrument;
    }

    public void setInstrument(String instrument) {
        this._instrument = instrument;
    }

    public double getPrice() {
        return _price;
    }

    public void setPrice(double price) {
        this._price = price;
    }

    public double getQuantity() {
        return _quantity;
    }

    public void setQuantity(double quantity) {
        this._quantity = quantity;
    }

    @Override
    public String toString() {
        return _quantity + " @ " + _price;
    }

    private String _instrument;
    private double _price;
    private double _quantity;
}
