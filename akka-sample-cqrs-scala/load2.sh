#!/bin/bash

declare -r UNIQUE="$1"

for i in {1..10}; do
  curl -X POST -H "Content-Type: application/json" -d '{"cartId":"cart-1-'${UNIQUE}'", "itemId":"item-'${i}'", "quantity":1}' http://127.0.0.1:8051/shopping/carts &
  curl -X POST -H "Content-Type: application/json" -d '{"cartId":"cart-2-'${UNIQUE}'", "itemId":"item-'${i}'", "quantity":1}' http://127.0.0.1:8051/shopping/carts &
  curl -X POST -H "Content-Type: application/json" -d '{"cartId":"cart-3-'${UNIQUE}'", "itemId":"item-'${i}'", "quantity":1}' http://127.0.0.1:8051/shopping/carts &
  sleep 0.1
done

sleep 5

for i in {11..20}; do
  curl -X POST -H "Content-Type: application/json" -d '{"cartId":"cart-1-'${UNIQUE}'", "itemId":"item-'${i}'", "quantity":1}' http://127.0.0.1:8051/shopping/carts &
  curl -X POST -H "Content-Type: application/json" -d '{"cartId":"cart-2-'${UNIQUE}'", "itemId":"item-'${i}'", "quantity":1}' http://127.0.0.1:8051/shopping/carts &
  curl -X POST -H "Content-Type: application/json" -d '{"cartId":"cart-3-'${UNIQUE}'", "itemId":"item-'${i}'", "quantity":1}' http://127.0.0.1:8051/shopping/carts &
  sleep 0.1
done

sleep 5

for i in {21..30}; do
  curl -X POST -H "Content-Type: application/json" -d '{"cartId":"cart-1-'${UNIQUE}'", "itemId":"item-'${i}'", "quantity":1}' http://127.0.0.1:8051/shopping/carts &
  curl -X POST -H "Content-Type: application/json" -d '{"cartId":"cart-2-'${UNIQUE}'", "itemId":"item-'${i}'", "quantity":1}' http://127.0.0.1:8051/shopping/carts &
  curl -X POST -H "Content-Type: application/json" -d '{"cartId":"cart-3-'${UNIQUE}'", "itemId":"item-'${i}'", "quantity":1}' http://127.0.0.1:8051/shopping/carts &
  sleep 0.1
done


