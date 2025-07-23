#!/bin/bash

# assuming the root image is kson_logo_transparent.svg we can regenerate the rest

# First, add a background to the transparent SVG
# Assuming the `g` node's last attirbute is `id="layer1"`, this will append a new rectangle (background)
# just inside the group
sed '/id="layer1">$/a\    <rect width="100%" height="100%" fill="#90bcfe"/>' kson_logo_transparent.svg > kson_logo_blue.svg

# Now create png versions of the transparent and blue svgs
inkscape kson_logo_transparent.svg --export-type=png --export-filename=kson_logo_transparent.png
inkscape kson_logo_transparent.svg --export-type=png --export-width=800 --export-height=800 --export-filename=kson_logo_transparent_800x800.png
inkscape kson_logo_blue.svg --export-type=png --export-filename=kson_logo_blue.png
inkscape kson_logo_blue.svg --export-type=png --export-width=800 --export-height=800 --export-filename=kson_logo_blue_800x800.png


