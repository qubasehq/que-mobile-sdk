/**
 * Unit tests for SemanticParser
 * Tests XML parsing, element deduplication, and UI representation generation
 */

import { SemanticParser } from '../SemanticParser'

describe('SemanticParser', () => {
  let parser: SemanticParser

  beforeEach(() => {
    parser = new SemanticParser()
  })

  describe('parse()', () => {
    it('should parse simple XML hierarchy', () => {
      const xml = `
        <hierarchy>
          <node class="android.widget.Button" resource-id="com.app:id/button1" text="Click Me" clickable="true" bounds="[100,200][300,400]" enabled="true" />
        </hierarchy>
      `

      const result = parser.parse(xml)

      expect(result.elements).toHaveLength(1)
      expect(result.elements[0]).toMatchObject({
        id: 0,
        isClickable: true,
        text: 'Click Me',
        resourceId: 'com.app:id/button1',
        className: 'android.widget.Button',
      })
      expect(result.elements[0].center).toEqual({ x: 200, y: 300 })
    })

    it('should deduplicate identical elements', () => {
      const xml = `
        <hierarchy>
          <node class="android.widget.Button" text="Button" clickable="true" bounds="[0,0][100,100]" />
          <node class="android.widget.Button" text="Button" clickable="true" bounds="[0,0][100,100]" />
        </hierarchy>
      `

      const result = parser.parse(xml)

      expect(result.elements).toHaveLength(1)
    })

    it('should handle nested nodes', () => {
      const xml = `
        <hierarchy>
          <node class="android.widget.LinearLayout" bounds="[0,0][1080,1920]">
            <node class="android.widget.Button" text="Button 1" clickable="true" bounds="[0,0][100,100]" />
            <node class="android.widget.Button" text="Button 2" clickable="true" bounds="[100,0][200,100]" />
          </node>
        </hierarchy>
      `

      const result = parser.parse(xml)

      expect(result.elements).toHaveLength(2)
      expect(result.elements[0].text).toBe('Button 1')
      expect(result.elements[1].text).toBe('Button 2')
    })

    it('should filter out non-interactive elements', () => {
      const xml = `
        <hierarchy>
          <node class="android.widget.Button" text="Clickable" clickable="true" bounds="[0,0][100,100]" />
          <node class="android.widget.View" clickable="false" bounds="[0,0][100,100]" />
        </hierarchy>
      `

      const result = parser.parse(xml)

      expect(result.elements).toHaveLength(1)
      expect(result.elements[0].text).toBe('Clickable')
    })

    it('should include elements with text even if not clickable', () => {
      const xml = `
        <hierarchy>
          <node class="android.widget.TextView" text="Important Text" clickable="false" bounds="[0,0][100,100]" />
        </hierarchy>
      `

      const result = parser.parse(xml)

      expect(result.elements).toHaveLength(1)
      expect(result.elements[0].text).toBe('Important Text')
    })

    it('should handle empty hierarchy', () => {
      const xml = '<hierarchy></hierarchy>'

      const result = parser.parse(xml)

      expect(result.elements).toHaveLength(0)
      expect(result.uiRepresentation).toBe('Empty screen - no elements found')
    })

    it('should handle invalid XML gracefully', () => {
      const xml = 'not valid xml'

      const result = parser.parse(xml)

      expect(result.elements).toHaveLength(0)
      expect(result.uiRepresentation).toBe('Empty screen - no elements found')
    })

    it('should build element map correctly', () => {
      const xml = `
        <hierarchy>
          <node class="android.widget.Button" text="Button 1" clickable="true" bounds="[0,0][100,100]" />
          <node class="android.widget.Button" text="Button 2" clickable="true" bounds="[100,0][200,100]" />
        </hierarchy>
      `

      const result = parser.parse(xml)

      expect(result.elementMap.size).toBe(2)
      expect(result.elementMap.get(0)?.text).toBe('Button 1')
      expect(result.elementMap.get(1)?.text).toBe('Button 2')
    })

    it('should generate UI representation with indentation', () => {
      const xml = `
        <hierarchy>
          <node class="android.widget.LinearLayout" bounds="[0,0][1080,1920]">
            <node class="android.widget.Button" text="Nested Button" clickable="true" bounds="[0,0][100,100]" />
          </node>
        </hierarchy>
      `

      const result = parser.parse(xml)

      expect(result.uiRepresentation).toContain('[0]')
      expect(result.uiRepresentation).toContain('Nested Button')
    })

    it('should parse bounds to center coordinates correctly', () => {
      const xml = `
        <hierarchy>
          <node class="android.widget.Button" text="Test" clickable="true" bounds="[100,200][300,400]" />
        </hierarchy>
      `

      const result = parser.parse(xml)

      expect(result.elements[0].center).toEqual({ x: 200, y: 300 })
      expect(result.elements[0].bounds).toBe('[100,200][300,400]')
    })

    it('should include content description in element description', () => {
      const xml = `
        <hierarchy>
          <node class="android.widget.ImageButton" content-desc="Settings" clickable="true" bounds="[0,0][100,100]" />
        </hierarchy>
      `

      const result = parser.parse(xml)

      expect(result.elements[0].description).toContain('Settings')
    })

    it('should include resource ID in element description', () => {
      const xml = `
        <hierarchy>
          <node class="android.widget.Button" resource-id="com.app:id/submit_button" text="Submit" clickable="true" bounds="[0,0][100,100]" />
        </hierarchy>
      `

      const result = parser.parse(xml)

      expect(result.elements[0].description).toContain('submit_button')
    })

    it('should include scrollable elements', () => {
      const xml = `
        <hierarchy>
          <node class="android.widget.ScrollView" scrollable="true" bounds="[0,0][1080,1920]" />
        </hierarchy>
      `

      const result = parser.parse(xml)

      expect(result.elements).toHaveLength(1)
      expect(result.elements[0].description).toContain('scrollable')
    })
  })
})
