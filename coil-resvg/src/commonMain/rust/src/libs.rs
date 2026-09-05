use std::sync::{Arc, OnceLock};
use tiny_skia::Pixmap;

uniffi::setup_scaffolding!();

static FONTDB_WITH_FONTS: OnceLock<Arc<fontdb::Database>> = OnceLock::new();

static FONTDB_EMPTY: OnceLock<Arc<fontdb::Database>> = OnceLock::new();

fn svg_has_text(svg_str: &str) -> bool {
    const KEYWORDS: &[&str] = &[
        "<text",
        ":text",
        "<tspan",
        ":tspan",
        "<foreignObject",
        ":foreignObject",
        "<altGlyph",
        ":altGlyph",
    ];

    KEYWORDS.iter().any(|&k| svg_str.contains(k))
}

fn get_empty_fontdb() -> Arc<fontdb::Database> {
    FONTDB_EMPTY
        .get_or_init(|| Arc::new(fontdb::Database::new()))
        .clone()
}

fn get_fontdb_with_fonts() -> Arc<fontdb::Database> {
    FONTDB_WITH_FONTS
        .get_or_init(|| {
            let mut fontdb = fontdb::Database::new();

            #[cfg(target_os = "android")]
            {
                fontdb.load_fonts_dir("/system/fonts");
                fontdb.set_sans_serif_family("Roboto");
                fontdb.set_serif_family("Noto Serif");
                fontdb.set_cursive_family("Roboto");
                fontdb.set_fantasy_family("Roboto");
                fontdb.set_monospace_family("Roboto Mono");
            }

            #[cfg(target_os = "ios")]
            {
                fontdb.load_fonts_dir("/System/Library/Fonts");
                fontdb.load_fonts_dir("/System/Library/Fonts/Core");
                fontdb.set_sans_serif_family("Helvetica Neue");
                fontdb.set_serif_family("Times New Roman");
                fontdb.set_monospace_family("Menlo");
                fontdb.set_cursive_family("Snell Roundhand");
                fontdb.set_fantasy_family("Papyrus");
            }

            #[cfg(not(any(target_os = "android", target_os = "ios")))]
            {
                fontdb.load_system_fonts();
            }

            Arc::new(fontdb)
        })
        .clone()
}

#[derive(uniffi::Record)]
pub struct SvgSize {
    pub width: f32,
    pub height: f32,
}

#[derive(uniffi::Record)]
pub struct SvgRenderResult {
    pub width: u32,
    pub height: u32,
    pub pixels: Vec<u8>,
}

#[derive(uniffi::Record)]
pub struct SvgRenderResultWithCache {
    pub image: SvgRenderResult,
    pub png: Option<Vec<u8>>,
}

#[derive(Debug, uniffi::Error, thiserror::Error)]
pub enum SvgError {
    #[error("Failed to parse SVG: {msg}")]
    ParseError { msg: String },
    #[error("Render failed: {msg}")]
    RenderError { msg: String },
}

#[derive(uniffi::Object)]
pub struct SvgRenderer {
    tree: usvg::Tree,
}

#[uniffi::export]
impl SvgRenderer {
    #[uniffi::constructor]
    pub fn from_data(svg_data: Vec<u8>) -> Result<Self, SvgError> {
        let svg_str = String::from_utf8(svg_data).map_err(|e| SvgError::ParseError {
            msg: format!("Invalid UTF-8 data: {}", e),
        })?;

        let fontdb = if svg_has_text(&svg_str) {
            get_fontdb_with_fonts()
        } else {
            get_empty_fontdb()
        };

        let mut options = usvg::Options::default();
        options.fontdb = fontdb;

        let tree = usvg::Tree::from_str(&svg_str, &options).map_err(|e| SvgError::ParseError {
            msg: format!("SVG parse failed: {}", e),
        })?;

        Ok(Self { tree })
    }

    pub fn render(&self, width: u32, height: u32) -> Result<SvgRenderResult, SvgError> {
        let pixmap = self.render_pixmap(width, height)?;
        Ok(into_render_result(pixmap))
    }

    /// Renders once and optionally encodes a PNG for the Kotlin disk cache.
    /// A PNG encoding error leaves the rendered image available with `png: None`.
    pub fn render_with_cache(
        &self,
        width: u32,
        height: u32,
        encode_png: bool,
    ) -> Result<SvgRenderResultWithCache, SvgError> {
        let pixmap = self.render_pixmap(width, height)?;
        // Cache encoding is optional: an encoding failure must not discard the rendered pixels.
        let png = if encode_png {
            pixmap.encode_png().ok()
        } else {
            None
        };
        Ok(SvgRenderResultWithCache {
            image: into_render_result(pixmap),
            png,
        })
    }

    pub fn get_size(&self) -> SvgSize {
        let size = self.tree.size();
        SvgSize {
            width: size.width(),
            height: size.height(),
        }
    }
}

impl SvgRenderer {
    fn render_pixmap(&self, width: u32, height: u32) -> Result<Pixmap, SvgError> {
        let size = self.tree.size();

        let target_width = if width == 0 {
            size.width().ceil() as u32
        } else {
            width
        };

        let target_height = if height == 0 {
            size.height().ceil() as u32
        } else {
            height
        };

        let mut pixmap =
            Pixmap::new(target_width, target_height).ok_or_else(|| SvgError::RenderError {
                msg: format!("Cannot create {}x{} pixmap", target_width, target_height),
            })?;

        let scale_x = target_width as f32 / size.width();
        let scale_y = target_height as f32 / size.height();

        let transform = tiny_skia::Transform::from_scale(scale_x, scale_y);

        resvg::render(&self.tree, transform, &mut pixmap.as_mut());

        Ok(pixmap)
    }
}

fn into_render_result(pixmap: Pixmap) -> SvgRenderResult {
    SvgRenderResult {
        width: pixmap.width(),
        height: pixmap.height(),
        pixels: pixmap.take(),
    }
}

#[uniffi::export]
pub fn render_svg(svg_data: Vec<u8>, width: u32, height: u32) -> Result<SvgRenderResult, SvgError> {
    let renderer = SvgRenderer::from_data(svg_data)?;
    renderer.render(width, height)
}

#[cfg(test)]
mod tests {
    use super::*;

    const SVG: &[u8] = br##"<svg xmlns="http://www.w3.org/2000/svg" width="3" height="1">
        <rect width="1" height="1" fill="#ff0000" fill-opacity="0.5"/>
        <rect x="1" width="1" height="1" fill="#0000ff"/>
    </svg>"##;

    #[test]
    fn cached_png_preserves_dimensions_and_transparency() {
        let renderer = SvgRenderer::from_data(SVG.to_vec()).unwrap();
        let result = renderer.render_with_cache(3, 1, true).unwrap();
        let decoded = Pixmap::decode_png(&result.png.unwrap()).unwrap();
        assert_eq!((decoded.width(), decoded.height()), (3, 1));
        assert_eq!(decoded.data(), result.image.pixels);
        assert_eq!(decoded.pixel(0, 0).unwrap().alpha(), 128);
        assert_eq!(decoded.pixel(1, 0).unwrap().alpha(), 255);
        assert_eq!(decoded.pixel(2, 0).unwrap().alpha(), 0);
    }

    #[test]
    fn disabling_encoding_returns_the_same_pixels_without_png() {
        let renderer = SvgRenderer::from_data(SVG.to_vec()).unwrap();
        let uncached = renderer.render_with_cache(30, 10, false).unwrap();
        let cached = renderer.render_with_cache(30, 10, true).unwrap();
        let original = renderer.render(30, 10).unwrap();
        assert!(uncached.png.is_none());
        assert!(cached.png.is_some());
        assert_eq!(uncached.image.pixels, cached.image.pixels);
        assert_eq!(uncached.image.pixels, original.pixels);
        assert_eq!((uncached.image.width, uncached.image.height), (30, 10));
    }
}
