#!/bin/bash

# Script to generate iOS app icons from a source image
# Requires ImageMagick (install with: brew install imagemagick)

SOURCE_IMAGE="Goose/Assets.xcassets/AppIcon.appiconset/goose-icon.svg"
ICONSET_DIR="Goose/Assets.xcassets/AppIcon.appiconset"

# Check if source image exists
if [ ! -f "$SOURCE_IMAGE" ]; then
    echo "Error: Source image not found at $SOURCE_IMAGE"
    exit 1
fi

# Check if ImageMagick is installed
if ! command -v magick &> /dev/null; then
    echo "ImageMagick is not installed. Installing..."
    brew install imagemagick
fi

echo "Generating iOS app icons..."

# iPhone icons
magick "$SOURCE_IMAGE" -resize 40x40 "$ICONSET_DIR/Icon-20@2x.png"
magick "$SOURCE_IMAGE" -resize 60x60 "$ICONSET_DIR/Icon-20@3x.png"
magick "$SOURCE_IMAGE" -resize 58x58 "$ICONSET_DIR/Icon-29@2x.png"
magick "$SOURCE_IMAGE" -resize 87x87 "$ICONSET_DIR/Icon-29@3x.png"
magick "$SOURCE_IMAGE" -resize 80x80 "$ICONSET_DIR/Icon-40@2x.png"
magick "$SOURCE_IMAGE" -resize 120x120 "$ICONSET_DIR/Icon-40@3x.png"
magick "$SOURCE_IMAGE" -resize 120x120 "$ICONSET_DIR/Icon-60@2x.png"
magick "$SOURCE_IMAGE" -resize 180x180 "$ICONSET_DIR/Icon-60@3x.png"

# iPad icons
magick "$SOURCE_IMAGE" -resize 20x20 "$ICONSET_DIR/Icon-20.png"
magick "$SOURCE_IMAGE" -resize 40x40 "$ICONSET_DIR/Icon-20@2x~ipad.png"
magick "$SOURCE_IMAGE" -resize 29x29 "$ICONSET_DIR/Icon-29.png"
magick "$SOURCE_IMAGE" -resize 58x58 "$ICONSET_DIR/Icon-29@2x~ipad.png"
magick "$SOURCE_IMAGE" -resize 40x40 "$ICONSET_DIR/Icon-40~ipad.png"
magick "$SOURCE_IMAGE" -resize 80x80 "$ICONSET_DIR/Icon-40@2x~ipad.png"
magick "$SOURCE_IMAGE" -resize 76x76 "$ICONSET_DIR/Icon-76.png"
magick "$SOURCE_IMAGE" -resize 152x152 "$ICONSET_DIR/Icon-76@2x.png"
magick "$SOURCE_IMAGE" -resize 167x167 "$ICONSET_DIR/Icon-83.5@2x.png"

# App Store icon
magick "$SOURCE_IMAGE" -resize 1024x1024 "$ICONSET_DIR/Icon-1024.png"

echo "Icon generation complete!"
