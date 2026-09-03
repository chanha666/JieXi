Add-Type -AssemblyName System.Drawing

$resourceDir = Join-Path $PSScriptRoot '..\desktopApp\src\main\resources'
$resourceDir = [System.IO.Path]::GetFullPath($resourceDir)
[System.IO.Directory]::CreateDirectory($resourceDir) | Out-Null

$size = 512
$bitmap = [System.Drawing.Bitmap]::new($size, $size, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
$graphics = [System.Drawing.Graphics]::FromImage($bitmap)
$graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
$graphics.Clear([System.Drawing.Color]::Transparent)

$background = [System.Drawing.SolidBrush]::new([System.Drawing.Color]::FromArgb(255, 255, 251, 245))
$borderPen = [System.Drawing.Pen]::new([System.Drawing.Color]::FromArgb(255, 235, 222, 205), 5)
$borderPen.Alignment = [System.Drawing.Drawing2D.PenAlignment]::Inset
$shape = [System.Drawing.Drawing2D.GraphicsPath]::new()
$radius = 104
$diameter = $radius * 2
$shape.AddArc(16, 16, $diameter, $diameter, 180, 90)
$shape.AddArc($size - 16 - $diameter, 16, $diameter, $diameter, 270, 90)
$shape.AddArc($size - 16 - $diameter, $size - 16 - $diameter, $diameter, $diameter, 0, 90)
$shape.AddArc(16, $size - 16 - $diameter, $diameter, $diameter, 90, 90)
$shape.CloseFigure()
$graphics.FillPath($background, $shape)
$graphics.DrawPath($borderPen, $shape)

$linkPen = [System.Drawing.Pen]::new([System.Drawing.Color]::FromArgb(255, 55, 45, 37), 40)
$linkPen.StartCap = [System.Drawing.Drawing2D.LineCap]::Round
$linkPen.EndCap = [System.Drawing.Drawing2D.LineCap]::Round
$linkPen.LineJoin = [System.Drawing.Drawing2D.LineJoin]::Round

$graphics.TranslateTransform(256, 256)
$graphics.RotateTransform(-43)
$graphics.TranslateTransform(-256, -256)

$left = [System.Drawing.Drawing2D.GraphicsPath]::new()
$left.AddBezier(101, 279, 101, 184, 205, 154, 272, 213)
$left.AddBezier(272, 213, 308, 246, 302, 286, 275, 313)
$left.AddBezier(275, 313, 238, 351, 179, 349, 141, 313)
$graphics.DrawPath($linkPen, $left)

$right = [System.Drawing.Drawing2D.GraphicsPath]::new()
$right.AddBezier(411, 233, 411, 328, 307, 358, 240, 299)
$right.AddBezier(240, 299, 204, 266, 210, 226, 237, 199)
$right.AddBezier(237, 199, 274, 161, 333, 163, 371, 199)
$graphics.DrawPath($linkPen, $right)
$graphics.ResetTransform()

# A restrained orange centre mark gives the resolver its own identity without
# turning the icon into a coloured tile.
$accentBrush = [System.Drawing.SolidBrush]::new([System.Drawing.Color]::FromArgb(255, 199, 101, 39))
$graphics.FillEllipse($accentBrush, 236, 236, 40, 40)

$pngPath = Join-Path $resourceDir 'icon.png'
$bitmap.Save($pngPath, [System.Drawing.Imaging.ImageFormat]::Png)

$iconPath = Join-Path $resourceDir 'icon.ico'
$iconSizes = @(16, 20, 24, 32, 40, 48, 64, 128, 256)
$iconImages = @()
foreach ($iconSize in $iconSizes) {
    $scaled = [System.Drawing.Bitmap]::new($iconSize, $iconSize, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $scaledGraphics = [System.Drawing.Graphics]::FromImage($scaled)
    $scaledGraphics.CompositingMode = [System.Drawing.Drawing2D.CompositingMode]::SourceCopy
    $scaledGraphics.CompositingQuality = [System.Drawing.Drawing2D.CompositingQuality]::HighQuality
    $scaledGraphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $scaledGraphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality
    $scaledGraphics.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
    $scaledGraphics.DrawImage($bitmap, 0, 0, $iconSize, $iconSize)
    $memory = [System.IO.MemoryStream]::new()
    $scaled.Save($memory, [System.Drawing.Imaging.ImageFormat]::Png)
    $iconImages += ,$memory.ToArray()
    $memory.Dispose()
    $scaledGraphics.Dispose()
    $scaled.Dispose()
}

# Write a standards-compliant multi-resolution ICO. Icon.Save(GetHicon()) only
# emits a fragile single-size Windows handle and produced corrupted shell icons.
$iconStream = [System.IO.File]::Create($iconPath)
$writer = [System.IO.BinaryWriter]::new($iconStream)
$writer.Write([UInt16]0)
$writer.Write([UInt16]1)
$writer.Write([UInt16]$iconImages.Count)
$imageOffset = 6 + (16 * $iconImages.Count)
for ($index = 0; $index -lt $iconImages.Count; $index++) {
    $iconSize = $iconSizes[$index]
    $dimensionByte = if ($iconSize -eq 256) { [Byte]0 } else { [Byte]$iconSize }
    $writer.Write($dimensionByte)
    $writer.Write($dimensionByte)
    $writer.Write([Byte]0)
    $writer.Write([Byte]0)
    $writer.Write([UInt16]1)
    $writer.Write([UInt16]32)
    $writer.Write([UInt32]$iconImages[$index].Length)
    $writer.Write([UInt32]$imageOffset)
    $imageOffset += $iconImages[$index].Length
}
foreach ($iconImage in $iconImages) { $writer.Write($iconImage) }
$writer.Dispose()
$iconStream.Dispose()

$left.Dispose()
$right.Dispose()
$linkPen.Dispose()
$accentBrush.Dispose()
$shape.Dispose()
$borderPen.Dispose()
$background.Dispose()
$graphics.Dispose()
$bitmap.Dispose()

Write-Output $pngPath
Write-Output $iconPath
